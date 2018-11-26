package io.sailrocket.core.client.netty;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import io.sailrocket.core.util.Util;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Http1xRawBytesHandler extends BaseRawBytesHandler {
   private static final Logger log = LoggerFactory.getLogger(Http1xRawBytesHandler.class);
   private static final byte CR = 13;
   private static final byte LF = 10;
   private static final int MAX_LINE_LENGTH = 4096;

   private boolean crRead = false;
   private int contentLength = -1;
   private ByteBuf lastLine;
   private int status = 0;
   private boolean chunked = false;
   private boolean headersParsed = false;
   private boolean expectTrailers = false;
   private int skipChunkBytes;

   public Http1xRawBytesHandler(HttpConnection connection) {
      super(connection);
   }

   @Override
   protected boolean isRequestStream(int streamId) {
      return true;
   }

   @Override
   public void handlerAdded(ChannelHandlerContext ctx) {
      if (lastLine == null) {
         lastLine = ctx.alloc().buffer(MAX_LINE_LENGTH);
      }
   }

   @Override
   public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      if (lastLine != null) {
         lastLine.release();
         lastLine = null;
      }
      super.channelInactive(ctx);
   }

   @Override
   public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      if (lastLine != null) {
         lastLine.release();
         lastLine = null;
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
         ByteBuf buf = (ByteBuf) msg;
         if (expectTrailers) {
            if (!readTrailers(ctx, buf, buf.readerIndex(), buf.readerIndex() - lastLine.writerIndex())) {
               passFullBuffer(ctx, buf);
            }
         } else if (chunked && headersParsed) {
            if (skipChunkBytes > buf.readableBytes()) {
               skipChunkBytes -= buf.readableBytes();
               passFullBuffer(ctx, buf);
            } else {
               readChunks(ctx, buf, buf.readerIndex() + skipChunkBytes, buf.readerIndex() + skipChunkBytes);
            }
         } else if (responseBytes > 0) {
            // this implies headers parsed
            reset();
            handleBuffer(ctx, buf, 0);
         } else if (headersParsed) {
            // when this is not chunked transfer, we're finished processing headers and the content length is unknown
            // we'll pass everything until the connection is closed
            reset();
            passFullBuffer(ctx, buf);
         } else {
            int lineStartOffset = buf.readerIndex();
            int readerIndex = buf.readerIndex();
            int responseEnd = -1;
            // Read headers
            for (; readerIndex < buf.writerIndex(); ++readerIndex) {
               byte val = buf.getByte(readerIndex);
               if (val == CR) {
                  crRead = true;
               } else if (val == LF && crRead) {
                  try {
                     ByteBuf lineBuf;
                     if (readerIndex - lineStartOffset == 1 || lastLine.writerIndex() == 1 && readerIndex == 0) {
                        switch (connection.currentResponseHandlers(0).method()) {
                           case HEAD:
                           case CONNECT:
                              contentLength = 0;
                              chunked = false;
                        }
                        // empty line ends the headers
                        headersParsed = true;
                        if (contentLength >= 0) {
                           responseEnd = readerIndex + 1 + contentLength;
                           // reset content length, we won't need it further
                           contentLength = -1;
                        }
                        break;
                     } else if (lastLine.isReadable()) {
                        assert lineStartOffset == buf.readerIndex();
                        copyLastLine(buf, lineStartOffset, readerIndex);
                        lineBuf = lastLine;
                        lineStartOffset = 0;
                     } else {
                        lineBuf = buf;
                     }
                     if (status == 0) {
                        // skip HTTP version
                        int j = lineStartOffset;
                        for (; j < lineBuf.writerIndex(); ++j) {
                           if (lineBuf.getByte(j) == ' ') {
                              break;
                           }
                        }
                        status = readNumber(lineBuf, j);
                        if (status >= 100 && status < 200 || status == 204 || status == 304) {
                           contentLength = 0;
                        }
                     } else if (matches(lineBuf, lineStartOffset, HttpHeaderNames.CONTENT_LENGTH)) {
                        contentLength = readNumber(lineBuf, lineStartOffset + HttpHeaderNames.CONTENT_LENGTH.length() + 1);
                     } else if (matches(lineBuf, lineStartOffset, HttpHeaderNames.TRANSFER_ENCODING)) {
                        chunked = matches(lineBuf, lineStartOffset + HttpHeaderNames.TRANSFER_ENCODING.length() + 1, HttpHeaderValues.CHUNKED);
                     }
                  } finally {
                     crRead = false;
                     lastLine.writerIndex(0);
                     lineStartOffset = readerIndex + 1;
                  }
               } else {
                  crRead = false;
               }
            }
            if (!headersParsed) {
               copyLastLine(buf, lineStartOffset, buf.writerIndex());
            } else if (chunked) {
               // Here we're just after headers
               readChunks(ctx, buf, readerIndex, lineStartOffset);
            } else if (responseEnd < 0) {
               // Either we're still in headers, chunked transfer or trailers or the body is delimited by closing the connection
               passFullBuffer(ctx, buf);
            } else {
               responseBytes = responseEnd - buf.readerIndex();
               reset();
               handleBuffer(ctx, buf, 0);
            }
         }
      } else {
         log.error("Unexpected message type: {}", msg);
         super.channelRead(ctx, msg);
      }
   }

   private void reset() {
      headersParsed = false;
      status = 0;
   }

   private void readChunks(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex, int lineStartOffset) throws Exception {
      for (; readerIndex < buf.writerIndex(); ++readerIndex) {
         byte val = buf.getByte(readerIndex);
         if (val == CR) {
            crRead = true;
         } else if (val == LF && crRead) {
            try {
               ByteBuf lineBuf = buf;
               if (lastLine.isReadable()) {
                  copyLastLine(buf, lineStartOffset, readerIndex);
                  lineBuf = lastLine;
                  lineStartOffset = 0;
               }
               int partSize = readNumber(lineBuf, lineStartOffset);
               if (partSize == 0) {
                  chunked = false;
                  expectTrailers = true;
                  break;
               } else if (readerIndex + 1 + partSize < buf.writerIndex()) {
                  readerIndex += partSize; // + 1 from for loop
               } else {
                  skipChunkBytes = readerIndex + 1 + partSize - buf.writerIndex();
                  break;
               }
            } finally {
               crRead = false;
               lastLine.writerIndex(0);
               lineStartOffset = readerIndex + 1;
            }
         } else {
            crRead = false;
         }
      }
      if (expectTrailers) {
         if (readTrailers(ctx, buf, readerIndex, lineStartOffset)) {
            return;
         }
      } else {
         copyLastLine(buf, lineStartOffset, buf.writerIndex());
      }
      passFullBuffer(ctx, buf);
   }

   private void copyLastLine(ByteBuf buf, int lineStartOffset, int readerIndex) {
      // copy last line (incomplete) to lastLine
      int lineBytes = readerIndex - lineStartOffset;
      if (lastLine.writerIndex() + lineBytes > lastLine.capacity()) {
         throw new IllegalStateException("Too long header line.");
      } else if (lineBytes > 0) {
         buf.getBytes(lineStartOffset, lastLine, lastLine.writerIndex(), lineBytes);
         lastLine.writerIndex(lastLine.writerIndex() + lineBytes);
      }
   }

   private void passFullBuffer(ChannelHandlerContext ctx, ByteBuf buf) {
      Consumer<ByteBuf> handler = connection.currentResponseHandlers(0).rawBytesHandler();
      invokeHandler(handler, buf);
      ctx.fireChannelRead(buf);
   }

   private boolean readTrailers(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex, int lineStartOffset) throws Exception {
      for (; readerIndex < buf.writerIndex(); ++readerIndex) {
         byte val = buf.getByte(readerIndex);
         if (val == CR) {
            crRead = true;
         } else if (val == LF && crRead) {
            if (readerIndex - lineStartOffset == 1) {
               // empty line ends the trailers and whole message
               responseBytes = readerIndex + 1 - buf.readerIndex();
               chunked = false;
               expectTrailers = false;
               reset();
               handleBuffer(ctx, buf, 0);
               return true;
            }
            lineStartOffset = readerIndex + 1;
         } else {
            crRead = false;
         }
      }
      // We don't analyze trailers so we have no need to copy last line, but we need to mark the length
      lastLine.writerIndex(readerIndex - lineStartOffset);
      return false;
   }

   private boolean matches(ByteBuf buf, int bufOffset, AsciiString string) {
      bufOffset = skipWhitespaces(buf, bufOffset);
      if (bufOffset + string.length() > buf.writerIndex()) {
         return false;
      }
      for (int i = 0; i < string.length(); ++i) {
          if (!Util.compareIgnoreCase(buf.getByte(bufOffset + i), string.byteAt(i))) {
             return false;
          }
      }
      return true;
   }

   private int readNumber(ByteBuf buf, int index) {
      index = skipWhitespaces(buf, index);
      int value = 0;
      for (; index < buf.writerIndex(); ++index) {
         byte b = buf.getByte(index);
         if (b < '0' || b > '9') {
            return value;
         }
         value = value * 10 + (b - '0');
      }
      // we expect that we've read the <CR><LF> and we should see them
      throw new IllegalStateException();
   }

   private int skipWhitespaces(ByteBuf buf, int index) {
      for (; index < buf.writerIndex(); ++index) {
         byte b = buf.getByte(index);
         if (b != ' ' && b != '\t') break;
      }
      return index;
   }
}
