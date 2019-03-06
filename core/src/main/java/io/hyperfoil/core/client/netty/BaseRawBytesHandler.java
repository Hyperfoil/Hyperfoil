package io.hyperfoil.core.client.netty;

import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.http.HttpResponseHandlers;

public abstract class BaseRawBytesHandler extends ChannelInboundHandlerAdapter {
   protected final HttpConnection connection;
   protected int responseBytes = 0;

   public BaseRawBytesHandler(HttpConnection connection) {
      this.connection = connection;
   }

   protected void handleBuffer(ChannelHandlerContext ctx, ByteBuf buf, int streamId) throws Exception {
      HttpRequest request = null;
      if (isRequestStream(streamId)) {
         request = connection.peekRequest(streamId);
      }
      if (buf.readableBytes() > responseBytes) {
         ByteBuf slice = buf.readRetainedSlice(responseBytes);
         invokeHandler(request, slice);
         ctx.fireChannelRead(slice);
         responseBytes = 0;
         channelRead(ctx, buf);
      } else {
         invokeHandler(request, buf);
         responseBytes -= buf.readableBytes();
         ctx.fireChannelRead(buf);
      }
   }

   protected abstract boolean isRequestStream(int streamId);

   protected void invokeHandler(HttpRequest request, ByteBuf buf) {
      HttpResponseHandlers handlers;
      if (request != null && (handlers = (HttpResponseHandlers) request.handlers()).hasRawBytesHandler()) {
         int readerIndex = buf.readerIndex();
         handlers.handleRawBytes(request, buf);
         if (buf.readerIndex() != readerIndex) {
            // TODO: maybe we could just reset the reader index?
            throw new IllegalStateException("Handler has changed readerIndex on the buffer!");
         }
      }
   }
}
