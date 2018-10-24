package io.sailrocket.core.client.netty;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public abstract class BaseRawBytesHandler extends ChannelInboundHandlerAdapter {
   protected final HttpConnection connection;
   protected int responseBytes = 0;

   public BaseRawBytesHandler(HttpConnection connection) {
      this.connection = connection;
   }

   protected void handleBuffer(ChannelHandlerContext ctx, ByteBuf buf, int streamId) throws Exception {
      Consumer<ByteBuf> handler = connection.currentResponseHandlers(streamId).rawBytesHandler();
      if (buf.readableBytes() > responseBytes) {
         ByteBuf slice = buf.readRetainedSlice(responseBytes);
         invokeHandler(handler, buf);
         ctx.fireChannelRead(slice);
         responseBytes = 0;
         channelRead(ctx, buf);
      } else {
         invokeHandler(handler, buf);
         responseBytes -= buf.readableBytes();
         ctx.fireChannelRead(buf);
      }
   }

   protected void invokeHandler(Consumer<ByteBuf> handler, ByteBuf buf) {
      if (handler != null) {
         int readerIndex = buf.readerIndex();
         handler.accept(buf);
         if (buf.readerIndex() != readerIndex) {
            // TODO: maybe we could just reset the reader index?
            throw new IllegalStateException("Handler has changed readerIndex on the buffer!");
         }
      }
   }
}
