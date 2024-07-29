package io.hyperfoil.http.connection;

import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

public class Http2RawResponseHandler extends BaseResponseHandler {
   private static final Logger log = LogManager.getLogger(Http2RawResponseHandler.class);

   private int streamId = -1;
   private byte[] frameHeader = new byte[FRAME_HEADER_LENGTH];
   private int frameHeaderIndex = 0;

   Http2RawResponseHandler(HttpConnection connection) {
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
         while (buf.isReadable()) {
            if (responseBytes <= 0) {
               // we haven't received the header yet
               if (frameHeaderIndex > 0) {
                  // we have received some short buffers
                  int maxBytes = Math.min(FRAME_HEADER_LENGTH - frameHeaderIndex, buf.readableBytes());
                  buf.readBytes(frameHeader, frameHeaderIndex, maxBytes);
                  frameHeaderIndex += maxBytes;
                  if (frameHeaderIndex >= FRAME_HEADER_LENGTH) {
                     ByteBuf wrapped = Unpooled.wrappedBuffer(frameHeader);
                     responseBytes = wrapped.getUnsignedMedium(0);
                     streamId = wrapped.getInt(5) & Integer.MAX_VALUE;
                     HttpRequest request = connection.peekRequest(streamId);
                     onRawData(request, wrapped, wrapped.readableBytes() == responseBytes);
                     ctx.fireChannelRead(wrapped);
                     frameHeaderIndex = 0;
                     if (!handleBuffer(ctx, buf, streamId)) {
                        break;
                     }
                  }
               } else if (buf.readableBytes() >= FRAME_HEADER_LENGTH) {
                  responseBytes = FRAME_HEADER_LENGTH + buf.getUnsignedMedium(buf.readerIndex());
                  streamId = buf.getInt(buf.readerIndex() + 5) & Integer.MAX_VALUE;
                  if (!handleBuffer(ctx, buf, streamId)) {
                     break;
                  }
               } else {
                  frameHeaderIndex = buf.readableBytes();
                  buf.readBytes(frameHeader, 0, frameHeaderIndex);
                  assert !buf.isReadable();
                  buf.release();
               }
            } else if (!handleBuffer(ctx, buf, streamId)) {
               break;
            }
         }
      } else {
         log.error("Unexpected message type: {}", msg);
         super.channelRead(ctx, msg);
      }
   }

}
