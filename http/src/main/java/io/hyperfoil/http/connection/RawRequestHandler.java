package io.hyperfoil.http.connection;

import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class RawRequestHandler extends ChannelOutboundHandlerAdapter {
   private static final Logger log = LogManager.getLogger(RawRequestHandler.class);
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
