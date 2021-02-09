package io.hyperfoil.http.connection;

import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public abstract class BaseResponseHandler extends ChannelInboundHandlerAdapter {
   protected final HttpConnection connection;
   protected int responseBytes = 0;

   public BaseResponseHandler(HttpConnection connection) {
      this.connection = connection;
   }

   protected void handleBuffer(ChannelHandlerContext ctx, ByteBuf buf, int streamId) throws Exception {
      HttpRequest request = null;
      if (isRequestStream(streamId)) {
         request = connection.peekRequest(streamId);
      }
      if (buf.readableBytes() > responseBytes) {
         ByteBuf slice = buf.readRetainedSlice(responseBytes);
         onRawData(request, slice, true);
         onCompletion(request);
         onData(ctx, slice);
         responseBytes = 0;
         channelRead(ctx, buf);
      } else {
         boolean isLastPart = buf.readableBytes() == responseBytes;
         if (request != null) {
            onRawData(request, buf, isLastPart);
         }
         responseBytes -= buf.readableBytes();
         if (isLastPart && request != null) {
            onCompletion(request);
         }
         onData(ctx, buf);
      }
   }

   protected abstract boolean isRequestStream(int streamId);

   protected void onRawData(HttpRequest request, ByteBuf data, boolean isLastPart) {
      // When the request times out it is marked as completed and handlers are removed
      // but the connection is not closed automatically.
      if (request != null && !request.isCompleted()) {
         int readerIndex = data.readerIndex();
         HttpResponseHandlers handlers = request.handlers();
         request.enter();
         try {
            handlers.handleRawResponse(request, data, data.readerIndex(), data.readableBytes(), isLastPart);
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

   protected void onData(ChannelHandlerContext ctx, ByteBuf buf) {
      ctx.fireChannelRead(buf);
   }

   protected void onStatus(int status) {
   }

   protected void onHeaderRead(ByteBuf buf, int startOfName, int endOfName, int startOfValue, int endOfValue) {
   }

   protected void onBodyPart(ByteBuf buf, int startOffset, int length, boolean isLastPart) {
   }

   protected void onCompletion(HttpRequest request) {
   }
}
