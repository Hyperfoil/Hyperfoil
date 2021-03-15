package io.hyperfoil.http.connection;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.http.config.Http;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.AppendableCharSequence;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http2Connection extends Http2EventAdapter implements HttpConnection {
   private static final Logger log = LoggerFactory.getLogger(Http2Connection.class);
   private static final boolean trace = log.isTraceEnabled();

   private final ChannelHandlerContext context;
   private final io.netty.handler.codec.http2.Http2Connection connection;
   private final Http2ConnectionEncoder encoder;
   private final IntObjectMap<HttpRequest> streams = new IntObjectHashMap<>();
   private final long clientMaxStreams;
   private final boolean secure;

   private HttpConnectionPool pool;
   private int aboutToSend;
   private long maxStreams;
   private Status status = Status.OPEN;
   private HttpRequest dispatchedRequest;
   private long lastUsed = System.nanoTime();

   Http2Connection(ChannelHandlerContext context,
                   io.netty.handler.codec.http2.Http2Connection connection,
                   Http2ConnectionEncoder encoder,
                   Http2ConnectionDecoder decoder,
                   HttpClientPool clientPool) {
      this.context = context;
      this.connection = connection;
      this.encoder = encoder;
      this.clientMaxStreams = this.maxStreams = clientPool.config().maxHttp2Streams();
      this.secure = clientPool.isSecure();

      Http2EventAdapter listener = new EventAdapter();

      connection.addListener(listener);
      decoder.frameListener(listener);
   }

   @Override
   public ChannelHandlerContext context() {
      return context;
   }

   @Override
   public void onAcquire() {
      assert aboutToSend >= 0;
      aboutToSend++;
   }

   @Override
   public boolean isAvailable() {
      return inFlight() < maxStreams;
   }

   @Override
   public int inFlight() {
      return streams.size() + aboutToSend;
   }

   public void incrementConnectionWindowSize(int increment) {
      try {
         io.netty.handler.codec.http2.Http2Stream stream = connection.connectionStream();
         connection.local().flowController().incrementWindowSize(stream, increment);
      } catch (Http2Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void close() {
      if (status == Status.OPEN) {
         status = Status.CLOSING;
         cancelRequests(Connection.SELF_CLOSED_EXCEPTION);
      }
      context.close();
   }

   @Override
   public String host() {
      return pool.clientPool().host();
   }

   @Override
   public String authority() {
      return pool.clientPool().authority();
   }

   @Override
   public void attach(HttpConnectionPool pool) {
      this.pool = pool;
   }

   public void request(HttpRequest request,
                       BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                       boolean injectHostHeader,
                       BiFunction<Session, Connection, ByteBuf> bodyGenerator) {
      assert aboutToSend > 0;
      aboutToSend--;
      HttpClientPool httpClientPool = pool.clientPool();

      ByteBuf buf = bodyGenerator != null ? bodyGenerator.apply(request.session, this) : null;

      if (request.path.contains(" ")) {
         int length = request.path.length();
         AppendableCharSequence temp = new AppendableCharSequence(length);
         boolean beforeQuestion = true;
         for (int i = 0; i < length; ++i) {
            if (request.path.charAt(i) == ' ') {
               if (beforeQuestion) {
                  temp.append('%');
                  temp.append('2');
                  temp.append('0');
               } else {
                  temp.append('+');
               }
            } else {
               if (request.path.charAt(i) == '?') {
                  beforeQuestion = false;
               }
               temp.append(request.path.charAt(i));
            }
         }
         request.path = temp.toString();
      }

      Http2Headers headers = new DefaultHttp2Headers().method(request.method.name()).scheme(httpClientPool.scheme())
            .path(request.path).authority(httpClientPool.authority());
      // HTTPS selects host via SNI headers, duplicate Host header could confuse the server/proxy
      if (injectHostHeader && !pool.clientPool().config().protocol().secure()) {
         headers.add(HttpHeaderNames.HOST, httpClientPool.authority());
      }
      if (buf != null && buf.readableBytes() > 0) {
         headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buf.readableBytes()));
      }

      HttpRequestWriterImpl writer = new HttpRequestWriterImpl(request, headers);
      if (headerAppenders != null) {
         for (BiConsumer<Session, HttpRequestWriter> headerAppender : headerAppenders) {
            headerAppender.accept(request.session, writer);
         }
      }
      if (HttpCache.get(request.session).isCached(request, writer)) {
         if (trace) {
            log.trace("#{} Request is completed from cache", request.session.uniqueId());
         }
         // prevent adding to available list twice
         if (streams.size() != maxStreams - 1) {
            pool.afterRequestSent(this);
         }
         request.handleCached();
         tryReleaseToPool();
         return;
      }

      assert context.executor().inEventLoop();
      int id = nextStreamId();
      streams.put(id, request);
      dispatchedRequest = request;
      ChannelPromise writePromise = context.newPromise();
      encoder.writeHeaders(context, id, headers, 0, buf == null, writePromise);
      if (buf != null) {
         writePromise = context.newPromise();
         encoder.writeData(context, id, buf, 0, true, writePromise);
      }
      writePromise.addListener(request);
      context.flush();
      dispatchedRequest = null;
      pool.afterRequestSent(this);
   }

   @Override
   public HttpRequest dispatchedRequest() {
      return dispatchedRequest;
   }

   @Override
   public HttpRequest peekRequest(int streamId) {
      return streams.get(streamId);
   }

   @Override
   public boolean removeRequest(int streamId, HttpRequest request) {
      throw new UnsupportedOperationException();
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
      return HttpVersion.HTTP_2_0;
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

   private int nextStreamId() {
      return connection.local().incrementAndGetNextStreamId();
   }

   @Override
   public String toString() {
      return "Http2Connection{" +
            context.channel().localAddress() + " -> " + context.channel().remoteAddress() +
            ", status=" + status +
            ", streams=" + streams.size() + "+" + aboutToSend + ":" + streams +
            '}';
   }

   void cancelRequests(Throwable cause) {
      for (Iterator<HttpRequest> iterator = streams.values().iterator(); iterator.hasNext(); ) {
         HttpRequest request = iterator.next();
         iterator.remove();
         pool.release(this, false, true);
         request.cancel(cause);
      }
   }

   private class EventAdapter extends Http2EventAdapter {

      @Override
      public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
         if (settings.maxConcurrentStreams() != null) {
            // The settings frame may be sent at any moment, e.g. when the connection
            // does not have ongoing request and therefore the pool == null
            maxStreams = Math.min(clientMaxStreams, settings.maxConcurrentStreams());
         }
      }

      @Override
      public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
         HttpRequest request = streams.get(streamId);
         if (request != null && !request.isCompleted()) {
            HttpResponseHandlers handlers = request.handlers();
            int code = -1;
            try {
               code = Integer.parseInt(headers.status().toString());
            } catch (NumberFormatException ignore) {
            }
            request.enter();
            try {
               handlers.handleStatus(request, code, "");
               for (Map.Entry<CharSequence, CharSequence> header : headers) {
                  handlers.handleHeader(request, header.getKey(), header.getValue());
               }
               if (endStream) {
                  handlers.handleBodyPart(request, Unpooled.EMPTY_BUFFER, 0, 0, true);
               }
            } finally {
               request.exit();
            }
            request.session.proceed();
         }
         if (endStream) {
            endStream(streamId);
         }
      }

      @Override
      public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
         int ack = super.onDataRead(ctx, streamId, data, padding, endOfStream);
         HttpRequest request = streams.get(streamId);
         if (request != null && !request.isCompleted()) {
            HttpResponseHandlers handlers = request.handlers();
            request.enter();
            try {
               handlers.handleBodyPart(request, data, data.readerIndex(), data.readableBytes(), endOfStream);
            } finally {
               request.exit();
            }
            request.session.proceed();
         }
         if (endOfStream) {
            endStream(streamId);
         }
         return ack;
      }

      @Override
      public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
         HttpRequest request = streams.get(streamId);
         if (request != null) {
            HttpResponseHandlers handlers = request.handlers();
            if (!request.isCompleted()) {
               request.enter();
               try {
                  // TODO: maybe add a specific handler because we don't need to terminate other streams
                  handlers.handleThrowable(request, new IOException("HTTP2 stream was reset"));
               } catch (SessionStopException e) {
                  if (streams.remove(streamId) == request) {
                     tryReleaseToPool();
                  }
                  throw e;
               } finally {
                  request.exit();
               }
               request.session.proceed();
            }
            request.release();
            if (streams.remove(streamId) == request) {
               tryReleaseToPool();
            }
         }
      }

      private void endStream(int streamId) {
         HttpRequest request = streams.get(streamId);
         if (request != null) {
            if (!request.isCompleted()) {
               request.enter();
               try {
                  request.handlers().handleEnd(request, true);
                  if (trace) {
                     log.trace("Completed response on {}", this);
                  }
               } catch (SessionStopException e) {
                  if (streams.remove(streamId) == request) {
                     tryReleaseToPool();
                  }
                  throw e;
               } finally {
                  request.exit();
               }
               request.session.proceed();
            }
            request.release();
            if (streams.remove(streamId) == request) {
               tryReleaseToPool();
            }
         }
      }
   }

   private void tryReleaseToPool() {
      lastUsed = System.nanoTime();
      HttpConnectionPool pool = this.pool;
      if (pool != null) {
         // If this connection was not available we make it available
         pool.release(Http2Connection.this, inFlight() == maxStreams - 1 && !isClosed(), true);
         pool.pulse();
      }
   }

   private class HttpRequestWriterImpl implements HttpRequestWriter {
      private final HttpRequest request;
      private final Http2Headers headers;

      HttpRequestWriterImpl(HttpRequest request, Http2Headers headers) {
         this.request = request;
         this.headers = headers;
      }

      @Override
      public HttpConnection connection() {
         return Http2Connection.this;
      }

      @Override
      public HttpRequest request() {
         return request;
      }

      @Override
      public void putHeader(CharSequence header, CharSequence value) {
         headers.add(header, value);
         HttpCache.get(request.session).requestHeader(request, header, value);
      }
   }
}
