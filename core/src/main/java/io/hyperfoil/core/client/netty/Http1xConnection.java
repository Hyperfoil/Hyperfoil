package io.hyperfoil.core.client.netty;

import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpRequestWriter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.hyperfoil.api.http.HttpResponseHandlers;
import io.hyperfoil.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {
   private static final Logger log = LoggerFactory.getLogger(Http1xConnection.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Deque<HttpRequest> inflights;
   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   private HttpConnectionPool pool;
   private ChannelHandlerContext ctx;
   // we can safely use non-atomic variables since the connection should be always accessed by single thread
   private int size;
   private boolean activated;
   private boolean closed;

   Http1xConnection(HttpClientPoolImpl client, BiConsumer<HttpConnection, Throwable> handler) {
      this.activationHandler = handler;
      this.inflights = new ArrayDeque<>(client.http.pipeliningLimit());
   }

   @Override
   public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      this.ctx = ctx;
      if (ctx.channel().isActive()) {
         checkActivated(ctx);
      }
   }

   @Override
   public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
      checkActivated(ctx);
   }

   private void checkActivated(ChannelHandlerContext ctx) {
      if (!activated) {
         activated = true;
         activationHandler.accept(this, null);
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof HttpResponse) {
         HttpResponse response = (HttpResponse) msg;
         HttpRequest request = inflights.peek();
         if (request == null) {
            if (HttpResponseStatus.REQUEST_TIMEOUT.equals(response.status())) {
               // HAProxy sends 408 when we allocate the connection but do not use it within 10 seconds.
               log.debug("Closing connection {} as server timed out waiting for our first request.", this);
            } else {
               log.error("Received unsolicited response (status {}) on {}, discarding: {}", response.status(), this, msg);
            }
            return;
         }
         if (request.isCompleted()) {
            log.trace("Request on connection {} has been already completed (error in handlers?), ignoring", this);
         } else {
            HttpResponseHandlers handlers = request.handlers();
            try {
               handlers.handleStatus(request, response.status().code(), response.status().reasonPhrase());
               for (Map.Entry<String, String> header : response.headers()) {
                  handlers.handleHeader(request, header.getKey(), header.getValue());
               }
            } catch (Throwable t) {
               log.error("Response processing failed on {}", t, this);
               handlers.handleThrowable(request, t);
            }
         }
      }
      if (msg instanceof HttpContent) {
         HttpRequest request = inflights.peek();
         // When previous handlers throw an error the request is already completed
         if (!request.isCompleted()) {
            HttpResponseHandlers handlers = request.handlers();
            try {
               ByteBuf data = ((HttpContent) msg).content();
               handlers.handleBodyPart(request, data, data.readerIndex(), data.readableBytes(), msg instanceof LastHttpContent);
            } catch (Throwable t) {
               log.error("Response processing failed on {}", t, this);
               handlers.handleThrowable(request, t);
            }
         }
      }
      if (msg instanceof LastHttpContent) {
         size--;
         HttpRequest request = inflights.poll();
         // When previous handlers throw an error the request is already completed
         if (!request.isCompleted()) {
            try {
               request.handlers().handleEnd(request, true);
               if (trace) {
                  log.trace("Completed response on {}", this);
               }
            } catch (Throwable t) {
               log.error("Response processing failed on {}", t, this);
               request.handlers().handleThrowable(request, t);
            }
         }

         // If this connection was not available we make it available
         // TODO: it would be better to check this in connection pool
         HttpConnectionPool pool = this.pool;
         if (size == pool.clientPool().config().pipeliningLimit() - 1) {
            pool.release(this);
            this.pool = null;
         }
         pool.pulse();
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      log.warn("Exception in {}", cause, this);
      cancelRequests(cause);
      ctx.close();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      cancelRequests(Connection.CLOSED_EXCEPTION);
   }

   private void cancelRequests(Throwable cause) {
      HttpRequest request;
      while ((request = inflights.poll()) != null) {
         if (!request.isCompleted()) {
            request.handlers().handleThrowable(request, cause);
            request.session.proceed();
         }
      }
   }

   @Override
   public void attach(HttpConnectionPool pool) {
      this.pool = pool;
   }

   @Override
   public void request(HttpRequest request, BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      size++;
      ByteBuf buf = bodyGenerator != null ? bodyGenerator.apply(request.session, request.connection()) : null;
      if (buf == null) {
         buf = Unpooled.EMPTY_BUFFER;
      }
      DefaultFullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, request.method.netty, request.path, buf, false);
      msg.headers().add(HttpHeaderNames.HOST, pool.clientPool().authority());
      if (buf.readableBytes() > 0) {
         msg.headers().add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
      }
      request.session.httpCache().beforeRequestHeaders(request);
      HttpRequestWriterImpl writer = new HttpRequestWriterImpl(request, msg);
      if (headerAppenders != null) {
         // TODO: allocation, if it's not eliminated we could store a reusable object
         for (BiConsumer<Session, HttpRequestWriter> headerAppender : headerAppenders) {
            headerAppender.accept(request.session, writer);
         }
      }
      assert ctx.executor().inEventLoop();
      if (request.session.httpCache().isCached(request, writer)) {
         if (trace) {
            log.trace("#{} Request is completed from cache", request.session.uniqueId());
         }
         request.handlers().handleEnd(request, false);
         --size;
         pool.release(this);
         pool = null;
         return;
      }
      inflights.add(request);
      ChannelPromise writePromise = ctx.newPromise();
      writePromise.addListener(request);
      ctx.writeAndFlush(msg, writePromise);
   }

   @Override
   public HttpRequest peekRequest(int streamId) {
      assert streamId == 0;
      return inflights.peek();
   }

   @Override
   public void setClosed() {
      this.closed = true;
   }

   @Override
   public boolean isClosed() {
      return closed;
   }

   @Override
   public boolean isSecure() {
      return pool.clientPool().isSecure();
   }

   @Override
   public ChannelHandlerContext context() {
      return ctx;
   }

   @Override
   public boolean isAvailable() {
      // Having pool not attached implies that the connection is not taken out of the pool
      // and therefore it's fully available
      return pool == null || size < pool.clientPool().config().pipeliningLimit();
   }

   @Override
   public int inFlight() {
      return size;
   }

   @Override
   public void close() {
      // We need to cancel requests manually before sending the FIN packet, otherwise the server
      // could give us an unexpected response before closing the connection with RST packet.
      cancelRequests(Connection.SELF_CLOSED_EXCEPTION);
      ctx.close();
   }

   @Override
   public String host() {
      return pool.clientPool().host();
   }

   @Override
   public String toString() {
      return "Http1xConnection{" +
            ctx.channel().localAddress() + " -> " + ctx.channel().remoteAddress() +
            ", size=" + size +
            '}';
   }

   private class HttpRequestWriterImpl implements HttpRequestWriter {
      private final HttpRequest request;
      private final DefaultFullHttpRequest msg;

      HttpRequestWriterImpl(HttpRequest request, DefaultFullHttpRequest msg) {
         this.request = request;
         this.msg = msg;
      }

      @Override
      public HttpConnection connection() {
         return Http1xConnection.this;
      }

      @Override
      public HttpRequest request() {
         return request;
      }

      @Override
      public void putHeader(CharSequence header, CharSequence value) {
         msg.headers().add(header, value);
         request.session.httpCache().requestHeader(request, header, value);
      }
   }
}
