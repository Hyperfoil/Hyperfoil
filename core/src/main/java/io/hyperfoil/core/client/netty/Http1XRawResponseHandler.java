package io.hyperfoil.core.client.netty;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Http1XRawResponseHandler extends BaseRawResponseHandler {
   private static final Logger log = LoggerFactory.getLogger(Http1XRawResponseHandler.class);
   private static final byte CR = 13;
   private static final byte LF = 10;
   private static final int MAX_LINE_LENGTH = 4096;

   private State state = State.STATUS;
   private boolean crRead = false;
   private int contentLength = -1;
   private ByteBuf lastLine;
   private int status = 0;
   private boolean chunked = false;
   private int skipChunkBytes;

   private enum State {
      STATUS,
      HEADERS,
      BODY,
      TRAILERS
   }

   Http1XRawResponseHandler(HttpConnection connection) {
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
   public void handlerRemoved(ChannelHandlerContext ctx) {
      if (lastLine != null) {
         lastLine.release();
         lastLine = null;
      }
   }

   @Override
   public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (msg instanceof ByteBuf) {
         ByteBuf buf = (ByteBuf) msg;
         int readerIndex = buf.readerIndex();
         while (true) {
            switch (state) {
               case STATUS:
                  readerIndex = readStatus(ctx, buf, readerIndex);
                  break;
               case HEADERS:
                  readerIndex = readHeaders(ctx, buf, readerIndex);
                  break;
               case BODY:
                  readerIndex = readBody(ctx, buf, readerIndex);
                  break;
               case TRAILERS:
                  readerIndex = readTrailers(ctx, buf, readerIndex);
                  break;
            }
            if (readerIndex < 0) {
               return;
            }
         }
      } else {
         log.error("Unexpected message type: {}", msg);
         super.channelRead(ctx, msg);
      }
   }


   private int readStatus(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex) {
      int lineStartIndex = buf.readerIndex();
      for (; readerIndex < buf.writerIndex(); ++readerIndex) {
         byte val = buf.getByte(readerIndex);
         if (val == CR) {
            crRead = true;
         } else if (val == LF && crRead) {
            crRead = false;
            ByteBuf lineBuf = buf;
            if (lastLine.isReadable()) {
               assert lineStartIndex == buf.readerIndex();
               copyLastLine(buf, lineStartIndex, readerIndex);
               lineBuf = lastLine;
               lineStartIndex = 0;
            }
            // skip HTTP version
            int j = lineStartIndex;
            for (; j < lineBuf.writerIndex(); ++j) {
               if (lineBuf.getByte(j) == ' ') {
                  break;
               }
            }
            status = readDecNumber(lineBuf, j);
            if (status >= 100 && status < 200 || status == 204 || status == 304) {
               contentLength = 0;
            }
            state = State.HEADERS;
            return readerIndex + 1;
         } else {
            crRead = false;
         }
      }
      copyLastLine(buf, lineStartIndex, readerIndex);
      passFullBuffer(ctx, buf);
      return -1;
   }

   private int readHeaders(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex) throws Exception {
      int lineStartIndex = readerIndex;
      for (; readerIndex < buf.writerIndex(); ++readerIndex) {
         byte val = buf.getByte(readerIndex);
         if (val == CR) {
            crRead = true;
         } else if (val == LF && crRead) {
            crRead = false;
            ByteBuf lineBuf;
            if (readerIndex - lineStartIndex == 1 || lastLine.writerIndex() == 1 && readerIndex == buf.readerIndex()) {
               // empty line ends the headers
               HttpRequest httpRequest = connection.peekRequest(0);
               // Unsolicited response 408 may not have a matching request
               if (httpRequest != null) {
                  switch (httpRequest.method) {
                     case HEAD:
                     case CONNECT:
                        contentLength = 0;
                        chunked = false;
                  }
               }
               if (contentLength >= 0) {
                  responseBytes = readerIndex - buf.readerIndex() + contentLength + 1;
                  reset();
                  handleBuffer(ctx, buf, 0);
                  // handleBuffer calls channelRead recursively so we must not continue reading here
                  return -1;
               } else {
                  state = State.BODY;
               }
               return readerIndex + 1;
            } else if (lastLine.isReadable()) {
               copyLastLine(buf, lineStartIndex, readerIndex);
               lineBuf = lastLine;
               lineStartIndex = 0;
            } else {
               lineBuf = buf;
            }
            if (matches(lineBuf, lineStartIndex, HttpHeaderNames.CONTENT_LENGTH)) {
               contentLength = readDecNumber(lineBuf, lineStartIndex + HttpHeaderNames.CONTENT_LENGTH.length() + 1);
            } else if (matches(lineBuf, lineStartIndex, HttpHeaderNames.TRANSFER_ENCODING)) {
               chunked = matches(lineBuf, lineStartIndex + HttpHeaderNames.TRANSFER_ENCODING.length() + 1, HttpHeaderValues.CHUNKED);
               skipChunkBytes = 0;
            }
            lastLine.writerIndex(0);
            lineStartIndex = readerIndex + 1;
         } else {
            crRead = false;
         }
      }
      copyLastLine(buf, lineStartIndex, readerIndex);
      passFullBuffer(ctx, buf);
      return -1;
   }

   private int readBody(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex) throws Exception {
      if (chunked) {
         int readable = buf.writerIndex() - readerIndex;
         if (skipChunkBytes > readable) {
            skipChunkBytes -= readable;
            passFullBuffer(ctx, buf);
            return -1;
         } else {
            readerIndex += skipChunkBytes;
            skipChunkBytes = 0;
            return readChunks(ctx, buf, readerIndex);
         }
      } else if (responseBytes > 0) {
         reset();
         handleBuffer(ctx, buf, 0);
         return -1;
      } else {
         // Body length is unknown and it is not chunked => the request is delimited by connection close
         passFullBuffer(ctx, buf);
         return -1;
      }
   }

   private int readChunks(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex) {
      int lineStartOffset = readerIndex;
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
               int partSize = readHexNumber(lineBuf, lineStartOffset);
               if (partSize == 0) {
                  chunked = false;
                  state = State.TRAILERS;
                  return readerIndex + 1;
               } else if (readerIndex + 3 + partSize < buf.writerIndex()) {
                  readerIndex += partSize; // + 1 from for loop, +2 below
                  if (buf.getByte(++readerIndex) != CR || buf.getByte(++readerIndex) != LF) {
                     throw new IllegalStateException("Chunk must end with CRLF!");
                  }
                  lineStartOffset = readerIndex + 1;
                  assert skipChunkBytes == 0;
               } else {
                  skipChunkBytes = readerIndex + 3 + partSize - buf.writerIndex();
                  passFullBuffer(ctx, buf);
                  return -1;
               }
            } finally {
               crRead = false;
               lastLine.writerIndex(0);
            }
         } else {
            crRead = false;
         }
      }
      copyLastLine(buf, lineStartOffset, buf.writerIndex());
      passFullBuffer(ctx, buf);
      return -1;
   }

   private int readTrailers(ChannelHandlerContext ctx, ByteBuf buf, int readerIndex) throws Exception {
      int lineStartIndex = readerIndex;
      for (; readerIndex < buf.writerIndex(); ++readerIndex) {
         byte val = buf.getByte(readerIndex);
         if (val == CR) {
            crRead = true;
         } else if (val == LF && crRead) {
            if (readerIndex - lineStartIndex == 1 || lastLine.writerIndex() == 1 && readerIndex == buf.readerIndex()) {
               // empty line ends the trailers and whole message
               responseBytes = readerIndex + 1 - buf.readerIndex();
               reset();
               handleBuffer(ctx, buf, 0);
               return -1;
            }
            lineStartIndex = readerIndex + 1;
         } else {
            crRead = false;
         }
      }
      // We don't analyze trailers so we have no need to copy last line, but we need to mark the length
      lastLine.writerIndex(readerIndex - lineStartIndex);
      passFullBuffer(ctx, buf);
      return -1;
   }

   private void reset() {
      state = State.STATUS;
      status = 0;
      chunked = false;
      skipChunkBytes = 0;
      contentLength = -1;
      lastLine.writerIndex(0);
      crRead = false;
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
      HttpRequest request = connection.peekRequest(0);
      // Note: we cannot reliably know if this is the last part as the body might be delimited by closing the connection.
      invokeHandler(request, buf, buf.readerIndex(), buf.readableBytes(), false);
      ctx.fireChannelRead(buf);
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

   private int readHexNumber(ByteBuf buf, int index) {
      index = skipWhitespaces(buf, index);
      int value = 0;
      for (; index < buf.writerIndex(); ++index) {
         byte b = buf.getByte(index);
         int v = toHex((char) b);
         if (v < 0) {
            if (b != CR) {
               log.error("Error reading buffer, starting from {}, current index {} (char: {}), status {}:\n{}",
                     buf.readerIndex(), index, b, this,
                     ByteBufUtil.prettyHexDump(buf, buf.readerIndex(), buf.readableBytes()));
               throw new IllegalStateException("Part size must be followed by CRLF!");
            }
            return value;
         }
         value = value * 16 + v;
      }
      // we expect that we've read the <CR><LF> and we should see them
      throw new IllegalStateException();
   }

   private int toHex(char c) {
      if (c >= '0' && c <= '9') {
         return c - '0';
      } else if (c >= 'a' && c <= 'f') {
         return c - 'a' + 10;
      } else if (c >= 'A' && c <= 'F') {
         return c - 'A' + 10;
      } else {
         return -1;
      }
   }

   private int readDecNumber(ByteBuf buf, int index) {
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

   @Override
   public String toString() {
      return "Http1xRawBytesHandler{" +
            "state=" + state +
            ", crRead=" + crRead +
            ", contentLength=" + contentLength +
            ", status=" + status +
            ", chunked=" + chunked +
            ", skipChunkBytes=" + skipChunkBytes +
            '}';
   }
}
