package io.sailrocket.core.client.netty;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.sailrocket.api.http.HttpResponseHandlers;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Http1xConnection extends ChannelDuplexHandler implements HttpConnection {
   private static Logger log = LoggerFactory.getLogger(Http1xConnection.class);

   final HttpClientPoolImpl client;
   private final HttpConnectionPool pool;
   private final Deque<HttpStream> inflights;
   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   ChannelHandlerContext ctx;
   // we can safely use non-atomic variables since the connection should be always accessed by single thread
   private int size;
   private boolean activated;

   Http1xConnection(HttpClientPoolImpl client, HttpConnectionPool pool, BiConsumer<HttpConnection, Throwable> handler) {
      this.client = client;
      this.pool = pool;
      this.activationHandler = handler;
      this.inflights = new ArrayDeque<>(client.maxConcurrentStream);
   }

   HttpStream createStream(DefaultFullHttpRequest msg, HttpResponseHandlers handlers) {
      return new HttpStream(msg, handlers);
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
         HttpStream request = inflights.peek();
         if (request.handlers.statusHandler() != null) {
            request.handlers.statusHandler().accept(response.status().code());
         }
         if (request.handlers.headerHandler() != null) {
            for (Map.Entry<String, String> header : response.headers()) {
               request.handlers.headerHandler().accept(header.getKey(), header.getValue());
            }
         }
      }
      if (msg instanceof HttpContent) {
         HttpStream request = inflights.peek();
         if (request.handlers.dataHandler() != null) {
            request.handlers.dataHandler().accept(((HttpContent) msg).content());
         }
      }
      if (msg instanceof LastHttpContent) {
         size--;
         HttpStream request = inflights.poll();

         if (request.handlers.endHandler() != null) {
            request.handlers.endHandler().run();
         }
         request.handlers.setCompleted();
         log.trace("Completed response on {}", this);
         pool.pulse();
      }
      super.channelRead(ctx, msg);
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      log.warn("Exception in {}", cause, this);
      for (HttpStream request = inflights.poll(); request != null; request = inflights.poll()) {
         if (!request.handlers.isCompleted()) {
            request.handlers.exceptionHandler().accept(cause);
            request.handlers.setCompleted();
         }
      }
      ctx.close();
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      HttpStream request;
      while ((request = inflights.poll()) != null) {
         if (!request.handlers.isCompleted()) {
            request.handlers.exceptionHandler().accept(Connection.CLOSED_EXCEPTION);
            request.handlers.setCompleted();
         }
      }
   }

   @Override
   public HttpRequest request(HttpMethod method, String path, ByteBuf body) {
      size++;
      Http1xRequest request = new Http1xRequest(this, method, path, body);
      request.putHeader(HttpHeaderNames.HOST, client.authority);
      return request;
   }

   @Override
   public HttpResponseHandlers currentResponseHandlers(int streamId) {
      assert streamId == 0;
      return inflights.peek().handlers;
   }

   @Override
   public ChannelHandlerContext context() {
      return ctx;
   }

   @Override
   public boolean isAvailable() {
      return size < client.maxConcurrentStream;
   }

   @Override
   public void close() {
      ctx.close();
   }

   @Override
   public String host() {
      return client.host();
   }

   @Override
   public String toString() {
      return "Http1xConnection{" +
            ctx.channel().localAddress() + " -> " + ctx.channel().remoteAddress() +
            ", size=" + size +
            '}';
   }

   class HttpStream implements Runnable {

      private final DefaultFullHttpRequest msg;
      private final HttpResponseHandlers handlers;

      HttpStream(DefaultFullHttpRequest msg, HttpResponseHandlers handlers) {
         this.msg = msg;
         this.handlers = handlers;
      }

      @Override
      public void run() {
         ctx.writeAndFlush(msg);
         inflights.add(this);
      }
   }
}
