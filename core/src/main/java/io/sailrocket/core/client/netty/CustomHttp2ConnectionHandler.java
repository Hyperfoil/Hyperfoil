package io.sailrocket.core.client.netty;

import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;

import java.util.function.BiConsumer;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.internal.StringUtil;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.connection.HttpConnection;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class CustomHttp2ConnectionHandler extends io.netty.handler.codec.http2.Http2ConnectionHandler {
   private static final Logger log = LoggerFactory.getLogger(CustomHttp2ConnectionHandler.class);

   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   private final HttpConnectionPool connectionPool;
   private Http2Connection connection;

   CustomHttp2ConnectionHandler(
         HttpConnectionPool connectionPool,
         BiConsumer<HttpConnection, Throwable> activationHandler,
         Http2ConnectionDecoder decoder,
         Http2ConnectionEncoder encoder,
         Http2Settings initialSettings) {
      super(decoder, encoder, initialSettings);
      this.connectionPool = connectionPool;
      this.activationHandler = activationHandler;
   }

   private static String generateName(Class<? extends ChannelHandler> handlerType) {
      return StringUtil.simpleClassName(handlerType) + "#0";
   }

   @Override
   public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      super.handlerAdded(ctx);
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
      if (connection == null) {
         connection = new Http2Connection(ctx, connection(), encoder(), decoder(), connectionPool);
         // Use a very large stream window size
         connection.incrementConnectionWindowSize(1073676288 - 65535);
         ctx.pipeline().addBefore(generateName(CustomHttp2ConnectionHandler.class), null, new Http2RawBytesHandler(connection));
         activationHandler.accept(connection, null);
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      log.warn("Exception in {}", cause, this);
      if (getEmbeddedHttp2Exception(cause) != null) {
         onError(ctx, false, cause);
      } else {
         connection.cancelRequests(cause);
         ctx.close();
      }
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      connection.cancelRequests(Connection.CLOSED_EXCEPTION);
   }
}
