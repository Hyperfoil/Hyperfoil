package io.sailrocket.core.client.netty;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
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
   private int contentLength = 0;
   private byte[] lastLine = new byte[MAX_LINE_LENGTH];
   private int lastLineLength = 0;

   public Http1xRawBytesHandler(HttpConnection connection) {
      super(connection);
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
         ByteBuf buf = (ByteBuf) msg;
         if (responseBytes <= 0) {
            int startLine = buf.readerIndex();
            int responseEnd = -1;
            for (int i = buf.readerIndex(); i < buf.writerIndex(); ++i) {
               byte val = buf.getByte(i);
               if (val == CR) {
                  crRead = true;
               } else if (val == LF && crRead) {
                  try {
                     if (i - startLine == 1 || lastLineLength == 1 && i == 0) {
                        // empty line ends the headers
                        responseEnd = i + 1 + contentLength;
                        // reset content length, we won't need it further
                        contentLength = 0;
                        break;
                     } else if (lastLineLength > 0) {
                        assert startLine == buf.readerIndex();
                        if (lastLineLength + i - startLine > lastLine.length) {
                           throw new IllegalStateException("Too long header line.");
                        }
                        buf.getBytes(startLine, lastLine, lastLineLength, i - startLine);
                        if (matches(lastLine, lastLineLength, HttpHeaderNames.CONTENT_LENGTH)) {
                           contentLength = readNumber(lastLine, HttpHeaderNames.CONTENT_LENGTH.length() + 2);
                        }
                     } else {
                        if (matches(buf, startLine, HttpHeaderNames.CONTENT_LENGTH)) {
                           contentLength = readNumber(buf, startLine + HttpHeaderNames.CONTENT_LENGTH.length() + 2);
                        }
                     }
                  } finally {
                     crRead = false;
                     lastLineLength = 0;
                  }
               } else {
                  crRead = false;
               }
            }
            if (responseEnd < 0) {
               int lineBytes = buf.writerIndex() - startLine;
               if (lastLineLength + lineBytes > lastLine.length) {
                  throw new IllegalStateException("Too long header line.");
               }
               buf.getBytes(startLine, lastLine, lastLineLength, lineBytes);
               lastLineLength += lineBytes;
               // we haven't found the end of message so we can safely pass that to handler
               Consumer<ByteBuf> handler = connection.currentResponseHandlers(0).rawBytesHandler();
               invokeHandler(handler, buf);
               ctx.fireChannelRead(buf);
            } else {
               responseBytes = responseEnd - buf.readerIndex();
               handleBuffer(ctx, buf, 0);
            }
         } else {
            handleBuffer(ctx, buf, 0);
         }
      } else {
         log.error("Unexpected message type: {}", msg);
         super.channelRead(ctx, msg);
      }
   }

   private boolean matches(ByteBuf buf, int startIndex, AsciiString string) {
      if (startIndex + string.length() > buf.writerIndex()) {
         return false;
      }
      for (int i = 0; i < string.length(); ++i) {
          if (!Util.compareIgnoreCase(buf.getByte(startIndex + i), string.byteAt(i))) {
             return false;
          }
      }
      return true;
   }

   private boolean matches(byte[] buf, int bufLimit, AsciiString string) {
      if (string.length() > bufLimit) {
         return false;
      }
      for (int i = 0; i < string.length(); ++i) {
         if (!Util.compareIgnoreCase(buf[i], string.byteAt(i))) {
            return false;
         }
      }
      return true;
   }

   private int readNumber(ByteBuf buf, int index) {
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

   private int readNumber(byte[] buf, int index) {
      int value = 0;
      for (; index < buf.length; index++) {
         byte b = buf[index];
         if (b < '0' || b > '9') {
            return value;
         }
         value = value * 10 + (b - '0');
      }
      // we expect that we've read the <CR><LF> and we should see them
      throw new IllegalStateException();
   }
}
