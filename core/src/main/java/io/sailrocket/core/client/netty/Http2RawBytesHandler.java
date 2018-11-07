package io.sailrocket.core.client.netty;

import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Http2RawBytesHandler extends BaseRawBytesHandler {
   private static final Logger log = LoggerFactory.getLogger(Http2RawBytesHandler.class);

   private int streamId = -1;
   private byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
   private int frameHeaderIndex = 0;

   public Http2RawBytesHandler(HttpConnection connection) {
      super(connection);
   }

   @Override
   protected boolean isRequestStream(int streamId) {
      // 0 is any connection-related stuff
      // 1 is HTTP upgrade response
      // even numbers are server-initiated connections
      return (streamId & 1) == 1 && streamId >= 3;
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
         ByteBuf buf = (ByteBuf) msg;
         if (responseBytes <= 0) {
            // we haven't received the header yet
            if (frameHeaderIndex > 0) {
               // we have received some short buffers
               int maxBytes = Math.min(FRAME_HEADER_LENGTH - frameHeaderIndex, buf.readableBytes());
               buf.readBytes(frameHeader, frameHeaderIndex, maxBytes);
               frameHeaderIndex += maxBytes;
               if (frameHeaderIndex >= FRAME_HEADER_LENGTH) {
                  ByteBuf wrapped = Unpooled.wrappedBuffer(frameHeader);
                  responseBytes = FRAME_HEADER_LENGTH + wrapped.getUnsignedMedium(0);
                  streamId = wrapped.getInt(5) & Integer.MAX_VALUE;
                  Consumer<ByteBuf> handler = connection.currentResponseHandlers(streamId).rawBytesHandler();
                  invokeHandler(handler, wrapped);
                  ctx.fireChannelRead(wrapped);

                  handleBuffer(ctx, buf, streamId);
               }
            } else if (buf.readableBytes() >= FRAME_HEADER_LENGTH) {
               responseBytes = FRAME_HEADER_LENGTH + buf.getUnsignedMedium(buf.readerIndex());
               streamId = buf.getInt(buf.readerIndex() + 5) & Integer.MAX_VALUE;
               handleBuffer(ctx, buf, streamId);
            } else {
               frameHeaderIndex = buf.readableBytes();
               buf.readBytes(frameHeader, 0, frameHeaderIndex);
               assert !buf.isReadable();
               buf.release();
            }
         } else {
            handleBuffer(ctx, buf, streamId);
         }
      } else {
         log.error("Unexpected message type: {}", msg);
         super.channelRead(ctx, msg);
      }
   }

}
