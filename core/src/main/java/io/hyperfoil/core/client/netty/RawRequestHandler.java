package io.hyperfoil.core.client.netty;

import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class RawRequestHandler extends ChannelOutboundHandlerAdapter {
   private static final Logger log = LoggerFactory.getLogger(RawRequestHandler.class);
   private final HttpConnection connection;

   public RawRequestHandler(HttpConnection connection) {
      this.connection = connection;
   }

   @Override
   public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
      if (msg instanceof ByteBuf) {
         ByteBuf buf = (ByteBuf) msg;
         HttpRequest request = connection.dispatchedRequest();
         if (request != null) {
            if (request.handlers != null) {
               int readerIndex = buf.readerIndex();
               request.handlers.handleRawRequest(request, buf, readerIndex, buf.readableBytes());
               if (readerIndex != buf.readerIndex()) {
                  throw new IllegalStateException("Handler has changed readerIndex on the buffer!");
               }
            }
         }
         // non-request related data (SSL handshake, HTTP2 service messages...) will be ignored
      } else {
         log.warn("Unknown message being sent: {}", msg);
      }
      ctx.write(msg, promise);
   }
}
