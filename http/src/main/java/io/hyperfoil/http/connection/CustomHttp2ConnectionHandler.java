package io.hyperfoil.http.connection;

import static io.netty.handler.codec.http2.Http2CodecUtil.getEmbeddedHttp2Exception;

import java.io.IOException;
import java.util.function.BiConsumer;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnection;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.internal.StringUtil;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class CustomHttp2ConnectionHandler extends io.netty.handler.codec.http2.Http2ConnectionHandler {
   private static final Logger log = LogManager.getLogger(CustomHttp2ConnectionHandler.class);

   private final BiConsumer<HttpConnection, Throwable> activationHandler;
   private final HttpClientPool clientPool;
   private final boolean isUpgrade;
   private Http2Connection connection;

   CustomHttp2ConnectionHandler(
         HttpClientPool clientPool,
         BiConsumer<HttpConnection, Throwable> activationHandler,
         Http2ConnectionDecoder decoder,
         Http2ConnectionEncoder encoder,
         Http2Settings initialSettings, boolean isUpgrade) {
      super(decoder, encoder, initialSettings);
      this.clientPool = clientPool;
      this.activationHandler = activationHandler;
      this.isUpgrade = isUpgrade;
   }

   private static String generateName(Class<? extends ChannelHandler> handlerType) {
      return StringUtil.simpleClassName(handlerType) + "#0";
   }

   @Override
   public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      super.handlerAdded(ctx);
      if (ctx.channel().isActive() && !isUpgrade) {
         checkActivated(ctx);
      }
   }

   @Override
   public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
      if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
         checkActivated(ctx);
      } else if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED) {
         activationHandler.accept(null, new IOException("H2C upgrade was rejected by server."));
      }
      super.userEventTriggered(ctx, evt);
   }

   @Override
   public void channelActive(ChannelHandlerContext ctx) throws Exception {
      super.channelActive(ctx);
      checkActivated(ctx);
   }

   private void checkActivated(ChannelHandlerContext ctx) {
      if (connection == null) {
         connection = new Http2Connection(ctx, connection(), encoder(), decoder(), clientPool);
         // Use a very large stream window size
         connection.incrementConnectionWindowSize(1073676288 - 65535);
         if (clientPool.config().rawBytesHandlers()) {
            String customeHandlerName = generateName(CustomHttp2ConnectionHandler.class);
            ctx.pipeline().addBefore(customeHandlerName, null, new Http2RawResponseHandler(connection));
            ctx.pipeline().addBefore(customeHandlerName, null, new RawRequestHandler(connection));
         }
         activationHandler.accept(connection, null);
      }
   }

   @Override
   public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (cause != SessionStopException.INSTANCE) {
         log.warn("Exception in " + this, cause);
      }
      try {
         if (getEmbeddedHttp2Exception(cause) != null) {
            onError(ctx, false, cause);
         } else {
            if (connection != null) {
               connection.cancelRequests(cause);
            }
            ctx.close();
         }
      } catch (Throwable t) {
         log.error("Handling exception resulted in another exception", t);
      }
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) {
      connection.cancelRequests(Connection.CLOSED_EXCEPTION);
   }
}
