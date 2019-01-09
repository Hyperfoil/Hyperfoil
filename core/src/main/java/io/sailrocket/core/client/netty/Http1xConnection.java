package io.sailrocket.core.client.netty;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpVersion;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.HttpConnection;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.connection.HttpRequestWriter;
import io.sailrocket.api.http.HttpMethod;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.sailrocket.api.http.HttpResponseHandlers;
import io.sailrocket.api.session.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {
   private static Logger log = LoggerFactory.getLogger(Http1xConnection.class);

   private final HttpConnectionPool pool;
   private final Deque<Request> inflights;
   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   ChannelHandlerContext ctx;
   // we can safely use non-atomic variables since the connection should be always accessed by single thread
   private int size;
   private boolean activated;

   Http1xConnection(HttpClientPoolImpl client, HttpConnectionPool pool, BiConsumer<HttpConnection, Throwable> handler) {
      this.pool = pool;
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
         Request request = inflights.peek();
         HttpResponseHandlers handlers = (HttpResponseHandlers) request.handlers();
         handlers.handleStatus(request, response.status().code());
         for (Map.Entry<String, String> header : response.headers()) {
            handlers.handleHeader(request, header.getKey(), header.getValue());
         }
      }
      if (msg instanceof HttpContent) {
         Request request = inflights.peek();
         HttpResponseHandlers handlers = (HttpResponseHandlers) request.handlers();
         handlers.handleBodyPart(request, ((HttpContent) msg).content());
      }
      if (msg instanceof LastHttpContent) {
         size--;
         Request request = inflights.poll();
         request.handlers().handleEnd(request);
         log.trace("Completed response on {}", this);
         // If this connection was not available we make it available
         // TODO: it would be better to check this in connection pool
         if (size == pool.clientPool().config().pipeliningLimit() - 1) {
            pool.release(this);
         }
         pool.pulse();
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      log.warn("Exception in {}", cause, this);
      Request request;
      while ((request = inflights.poll()) != null) {
         if (!request.isCompleted()) {
            request.handlers().handleThrowable(request, cause);
            request.setCompleted();
            request.session.proceed();
         }
      }
      ctx.close();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      Request request;
      while ((request = inflights.poll()) != null) {
         if (!request.isCompleted()) {
            request.handlers().handleThrowable(request, Connection.CLOSED_EXCEPTION);
            request.setCompleted();
            request.session.proceed();
         }
      }
   }

   @Override
   public void request(Request request, HttpMethod method, Function<Session, String> pathGenerator, BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      size++;
      String path = pathGenerator.apply(request.session);
      ByteBuf buf = bodyGenerator != null ? bodyGenerator.apply(request.session, request.connection()) : null;
      if (buf == null) {
         buf = Unpooled.EMPTY_BUFFER;
      }
      DefaultFullHttpRequest msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method.netty, path, buf, false);
      msg.headers().add(HttpHeaderNames.HOST, pool.clientPool().authority());
      if (buf.readableBytes() > 0) {
         msg.headers().add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
      }
      if (headerAppenders != null) {
         // TODO: allocation, if it's not eliminated we could store a reusable object
         HttpRequestWriter writer = new HttpRequestWriterImpl(msg);
         for (BiConsumer<Session, HttpRequestWriter> headerAppender : headerAppenders) {
            headerAppender.accept(request.session, writer);
         }
      }
      assert ctx.executor().inEventLoop();
      inflights.add(request);
      ctx.writeAndFlush(msg);
   }

   @Override
   public Request peekRequest(int streamId) {
      assert streamId == 0;
      return inflights.peek();
   }

   @Override
   public ChannelHandlerContext context() {
      return ctx;
   }

   @Override
   public boolean isAvailable() {
      return size < pool.clientPool().config().pipeliningLimit();
   }

   @Override
   public int inFlight() {
      return size;
   }

   @Override
   public void close() {
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
      private final DefaultFullHttpRequest msg;

      HttpRequestWriterImpl(DefaultFullHttpRequest msg) {
         this.msg = msg;
      }

      @Override
      public Connection connection() {
         return Http1xConnection.this;
      }

      @Override
      public void putHeader(CharSequence header, CharSequence value) {
         msg.headers().add(header, value);
      }
   }
}
