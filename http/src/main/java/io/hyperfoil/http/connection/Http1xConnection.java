package io.hyperfoil.http.connection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.impl.Util;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.http.config.Http;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {
   private static final Logger log = LogManager.getLogger(Http1xConnection.class);
   private static final boolean trace = log.isTraceEnabled();
   private static final byte[] HTTP1_1 = { ' ', 'H', 'T', 'T', 'P', '/', '1', '.', '1', '\r', '\n' };

   private final Deque<HttpRequest> inflights;
   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   private final boolean secure;
   private final int pipeliningLimit;

   private HttpConnectionPool pool;
   private ChannelHandlerContext ctx;
   private int aboutToSend;
   private boolean activated;
   private Status status = Status.OPEN;
   private long lastUsed = System.nanoTime();

   Http1xConnection(HttpClientPoolImpl client, BiConsumer<HttpConnection, Throwable> handler) {
      this.activationHandler = handler;
      this.inflights = new ArrayDeque<>(client.config().pipeliningLimit());
      this.secure = client.isSecure();
      this.pipeliningLimit = client.config().pipeliningLimit();
   }

   @Override
   public void handlerAdded(ChannelHandlerContext ctx) {
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
   public void channelRead(ChannelHandlerContext ctx, Object msg) {
      // We should have handled that before
      throw new UnsupportedOperationException();
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (cause instanceof SessionStopException) {
         // ignore
      } else {
         log.warn("Exception in " + this, cause);
         cancelRequests(cause);
      }
      ctx.close();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      cancelRequests(CLOSED_EXCEPTION);
   }

   private void cancelRequests(Throwable cause) {
      HttpRequest request;
      while ((request = inflights.poll()) != null) {
         pool.release(this, false, true);
         if (request.isRunning()) {
            request.cancel(cause);
         }
      }
   }

   @Override
   public void attach(HttpConnectionPool pool) {
      this.pool = pool;
   }

   @Override
   public void request(HttpRequest request,
                       BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                       boolean injectHostHeader,
                       BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      assert aboutToSend > 0;
      aboutToSend--;
      ByteBuf buf = ctx.alloc().buffer();
      buf.writeBytes(request.method.netty.asciiName().array());
      buf.writeByte(' ');
      boolean beforeQuestion = true;
      for (int i = 0; i < request.path.length(); ++i) {
         if (request.path.charAt(i) == ' ') {
            if (beforeQuestion) {
               buf.writeByte(0xFF & '%');
               buf.writeByte(0xFF & '2');
               buf.writeByte(0xFF & '0');
            } else {
               buf.writeByte(0xFF & '+');
            }
         } else {
            if (request.path.charAt(i) == '?') {
               beforeQuestion = false;
            }
            buf.writeByte(0xFF & request.path.charAt(i));
         }
      }
      buf.writeBytes(HTTP1_1);

      if (injectHostHeader) {
         writeHeader(buf, HttpHeaderNames.HOST.array(), pool.clientPool().originalDestinationBytes());
      }
      // TODO: adjust interface - we can't send static buffers anyway
      ByteBuf body = bodyGenerator != null ? bodyGenerator.apply(request.session, request.connection()) : null;
      if (body == null) {
         body = Unpooled.EMPTY_BUFFER;
      }
      if (body.readableBytes() > 0) {
         if (trace) {
            log.trace("Sending HTTP request body: {}\n", Util.toString(body, body.readerIndex(), body.readableBytes()));
         }
         buf.writeBytes(HttpHeaderNames.CONTENT_LENGTH.array()).writeByte(':').writeByte(' ');
         Util.intAsText2byteBuf(body.readableBytes(), buf);
         buf.writeByte('\r').writeByte('\n');
      }

      HttpCache httpCache = HttpCache.get(request.session);
      httpCache.beforeRequestHeaders(request);
      // TODO: if headers are strings, UTF-8 conversion creates a lot of trash
      HttpRequestWriterImpl writer = new HttpRequestWriterImpl(request, buf);
      if (headerAppenders != null) {
         // TODO: allocation, if it's not eliminated we could store a reusable object
         for (BiConsumer<Session, HttpRequestWriter> headerAppender : headerAppenders) {
            headerAppender.accept(request.session, writer);
         }
      }
      buf.writeByte('\r').writeByte('\n');
      assert ctx.executor().inEventLoop();
      if (httpCache.isCached(request, writer)) {
         if (trace) {
            log.trace("#{} Request is completed from cache", request.session.uniqueId());
         }
         // prevent adding to available twice
         if (inFlight() != pipeliningLimit - 1) {
            pool.afterRequestSent(this);
         }
         request.handleCached();
         releasePoolAndPulse();
         return;
      }
      inflights.add(request);
      ChannelPromise writePromise = ctx.newPromise();
      writePromise.addListener(request);
      if (body.isReadable()) {
         ctx.write(buf);
         ctx.writeAndFlush(body, writePromise);
      } else {
         ctx.writeAndFlush(buf, writePromise);
      }
      pool.afterRequestSent(this);
   }

   private void writeHeader(ByteBuf buf, byte[] name, byte[] value) {
      buf.writeBytes(name).writeByte(':').writeByte(' ').writeBytes(value).writeByte('\r').writeByte('\n');
   }

   void releasePoolAndPulse() {
      lastUsed = System.nanoTime();
      // If this connection was not available we make it available
      HttpConnectionPool pool = this.pool;
      if (pool != null) {
         // Note: the pool might be already released if the completion handler
         // invoked another request which was served from cache.
         pool.release(this, inFlight() == pipeliningLimit - 1 && !isClosed(), true);
         pool.pulse();
      }
   }

   @Override
   public HttpRequest dispatchedRequest() {
      return inflights.peekLast();
   }

   @Override
   public HttpRequest peekRequest(int streamId) {
      assert streamId == 0;
      return inflights.peek();
   }

   @Override
   public boolean removeRequest(int streamId, HttpRequest request) {
      HttpRequest req = inflights.poll();
      if (req == null) {
         // cancel() was called before draining the queue
         return false;
      } else if (req != request) {
         throw new IllegalStateException();
      }
      return true;
   }

   @Override
   public void setClosed() {
      status = Status.CLOSED;
   }

   @Override
   public boolean isOpen() {
      return status == Status.OPEN;
   }

   @Override
   public boolean isClosed() {
      return status == Status.CLOSED;
   }

   @Override
   public boolean isSecure() {
      return secure;
   }

   @Override
   public HttpVersion version() {
      return HttpVersion.HTTP_1_1;
   }

   @Override
   public Http config() {
      return pool.clientPool().config();
   }

   @Override
   public HttpConnectionPool pool() {
      return pool;
   }

   @Override
   public long lastUsed() {
      return lastUsed;
   }

   @Override
   public ChannelHandlerContext context() {
      return ctx;
   }

   @Override
   public void onAcquire() {
      aboutToSend++;
   }

   @Override
   public boolean isAvailable() {
      // Having pool not attached implies that the connection is not taken out of the pool
      // and therefore it's fully available
      return pool == null || inFlight() < pipeliningLimit;
   }

   @Override
   public int inFlight() {
      return inflights.size() + aboutToSend;
   }

   @Override
   public void close() {
      if (status == Status.OPEN) {
         status = Status.CLOSING;
         // We need to cancel requests manually before sending the FIN packet, otherwise the server
         // could give us an unexpected response before closing the connection with RST packet.
         cancelRequests(SELF_CLOSED_EXCEPTION);
      }
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
            ", status=" + status +
            ", size=" + inflights.size() + "+" + aboutToSend + ":" + inflights + '}';
   }

   private class HttpRequestWriterImpl implements HttpRequestWriter {
      private final HttpRequest request;
      private final ByteBuf buf;

      HttpRequestWriterImpl(HttpRequest request, ByteBuf buf) {
         this.request = request;
         this.buf = buf;
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
         final ByteBuf buf = this.buf;
         buf.ensureWritable(header.length() + value.length() + 4);
         if (header instanceof AsciiString) {
            final AsciiString ascii = (AsciiString) header;
            // remove this when https://github.com/netty/netty/pull/13197 will be merged
            buf.writeBytes(ascii.array(), ascii.arrayOffset(), ascii.length());
         } else {
            // header name CANNOT be anything but US-ASCII: latin is already wider
            buf.writeCharSequence(header, CharsetUtil.ISO_8859_1);
         }
         buf.writeByte(':');
         buf.writeByte(' ');
         if (value instanceof AsciiString) {
            final AsciiString ascii = (AsciiString) value;
            // remove this when https://github.com/netty/netty/pull/13197 will be merged
            buf.writeBytes(ascii.array(), ascii.arrayOffset(), ascii.length());
         } else {
            if (Util.isLatin(value)) {
               buf.writeCharSequence(value, CharsetUtil.ISO_8859_1);
            } else {
               buf.writeCharSequence(value, CharsetUtil.UTF_8);
            }
         }
         buf.writeByte('\r');
         buf.writeByte('\n');
         HttpCache.get(request.session).requestHeader(request, header, value);
      }
   }
}
