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
         invokeHandler(request, slice, slice.readerIndex(), slice.readableBytes(), true);
         ctx.fireChannelRead(slice);
         responseBytes = 0;
         channelRead(ctx, buf);
      } else {
         invokeHandler(request, buf, buf.readerIndex(), buf.readableBytes(), buf.readableBytes() == responseBytes);
         responseBytes -= buf.readableBytes();
         ctx.fireChannelRead(buf);
      }
   }

   protected abstract boolean isRequestStream(int streamId);

   protected void invokeHandler(HttpRequest request, ByteBuf data, int offset, int length, boolean isLastPart) {
      // When the request times out it is marked as completed and handlers are removed
      // but the connection is not closed automatically.
      if (request != null && !request.isCompleted()) {
         int readerIndex = data.readerIndex();
         HttpResponseHandlers handlers = request.handlers();
         request.enter();
         try {
            handlers.handleRawBytes(request, data, offset, length, isLastPart);
         } catch (Throwable t) {
            handlers.handleThrowable(request, t);
         } finally {
            request.exit();
         }
         request.session.proceed();
         if (data.readerIndex() != readerIndex) {
            // TODO: maybe we could just reset the reader index?
            throw new IllegalStateException("Handler has changed readerIndex on the buffer!");
         }
      }
   }
}
