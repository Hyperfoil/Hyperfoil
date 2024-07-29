package io.hyperfoil.http.connection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.session.SessionStopException;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.impl.Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;

public class Http1xResponseHandler extends BaseResponseHandler {
   private static final Logger log = LogManager.getLogger(Http1xResponseHandler.class);
   private static final boolean trace = log.isTraceEnabled();
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

   Http1xResponseHandler(HttpConnection connection) {
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
            onStatus(status);
            state = State.HEADERS;
            lastLine.writerIndex(0);
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
      int lineEndIndex;
      for (; readerIndex < buf.writerIndex();) {
         if (!crRead) {
            final int indexOfCr = buf.indexOf(readerIndex, buf.writerIndex(), CR);
            if (indexOfCr == -1) {
               readerIndex = buf.writerIndex();
               break;
            }
            crRead = true;
            readerIndex = indexOfCr + 1;
         } else {
            byte val = buf.getByte(readerIndex);
            if (val == LF) {
               crRead = false;
               ByteBuf lineBuf;
               // lineStartIndex is valid only if lastLine is empty - otherwise we would ignore an incomplete line
               // in the buffer
               if (readerIndex - lineStartIndex == 1 && lastLine.writerIndex() == 0
                     || lastLine.writerIndex() == 1 && readerIndex == buf.readerIndex()) {
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
                  state = State.BODY;
                  lastLine.writerIndex(0);
                  if (contentLength >= 0) {
                     responseBytes = readerIndex - buf.readerIndex() + contentLength + 1;
                  }
                  return readerIndex + 1;
               } else if (lastLine.isReadable()) {
                  copyLastLine(buf, lineStartIndex, readerIndex);
                  lineBuf = lastLine;
                  lineEndIndex = lastLine.readableBytes() + readerIndex - lineStartIndex - 1; // account the CR
                  lineStartIndex = 0;
               } else {
                  lineBuf = buf;
                  lineEndIndex = readerIndex - 1; // account the CR
               }
               if (matches(lineBuf, lineStartIndex, HttpHeaderNames.CONTENT_LENGTH)) {
                  contentLength = readDecNumber(lineBuf, lineStartIndex + HttpHeaderNames.CONTENT_LENGTH.length() + 1);
               } else if (matches(lineBuf, lineStartIndex, HttpHeaderNames.TRANSFER_ENCODING)) {
                  chunked = matches(lineBuf, lineStartIndex + HttpHeaderNames.TRANSFER_ENCODING.length() + 1,
                        HttpHeaderValues.CHUNKED);
                  skipChunkBytes = 0;
               }
               int endOfNameIndex = lineEndIndex, startOfValueIndex = lineStartIndex;
               final int indexOfColon = lineBuf.indexOf(lineStartIndex, lineEndIndex, (byte) ':');
               if (indexOfColon != -1) {
                  final int i = indexOfColon;
                  for (endOfNameIndex = i - 1; endOfNameIndex >= lineStartIndex
                        && lineBuf.getByte(endOfNameIndex) == ' '; --endOfNameIndex)
                     ;
                  for (startOfValueIndex = i + 1; startOfValueIndex < lineEndIndex
                        && lineBuf.getByte(startOfValueIndex) == ' '; ++startOfValueIndex)
                     ;
               }
               onHeaderRead(lineBuf, lineStartIndex, endOfNameIndex + 1, startOfValueIndex, lineEndIndex);
               lastLine.writerIndex(0);
               lineStartIndex = readerIndex + 1;
            } else if (val != CR) {
               crRead = false;
            }
            ++readerIndex;
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
            int readableBody = Math.min(skipChunkBytes - 2, readable);
            skipChunkBytes -= readable;
            onBodyPart(buf, readerIndex, readableBody, false);
            passFullBuffer(ctx, buf);
            return -1;
         } else {
            // skipChunkBytes includes the CRLF
            onBodyPart(buf, readerIndex, skipChunkBytes - 2, false);
            readerIndex += skipChunkBytes;
            skipChunkBytes = 0;
            return readChunks(ctx, buf, readerIndex);
         }
      } else if (responseBytes > 0) {
         boolean isLastPart = buf.readableBytes() >= responseBytes;
         onBodyPart(buf, readerIndex, Math.min(buf.writerIndex(), buf.readerIndex() + responseBytes) - readerIndex, isLastPart);
         if (isLastPart) {
            reset();
         }
         return handleBuffer(ctx, buf, 0) ? buf.readerIndex() : -1;
      } else {
         // Body length is unknown and it is not chunked => the request is delimited by connection close
         // TODO: make sure we invoke this with isLastPart=true once
         onBodyPart(buf, readerIndex, buf.writerIndex() - readerIndex, false);
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
                  onBodyPart(Unpooled.EMPTY_BUFFER, 0, 0, true);
                  chunked = false;
                  state = State.TRAILERS;
                  return readerIndex + 1;
               } else if (readerIndex + 3 + partSize < buf.writerIndex()) {
                  onBodyPart(buf, readerIndex + 1, partSize, false);
                  readerIndex += partSize; // + 1 from for loop, +2 below
                  if (buf.getByte(++readerIndex) != CR || buf.getByte(++readerIndex) != LF) {
                     throw new IllegalStateException("Chunk must end with CRLF!");
                  }
                  lineStartOffset = readerIndex + 1;
                  assert skipChunkBytes == 0;
               } else {
                  onBodyPart(buf, readerIndex + 1, Math.min(buf.writerIndex() - readerIndex - 1, partSize), false);
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
               return handleBuffer(ctx, buf, 0) ? buf.readerIndex() : -1;
            }
            lineStartIndex = readerIndex + 1;
         } else {
            crRead = false;
         }
      }
      copyLastLine(buf, lineStartIndex, readerIndex);
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
      onRawData(request, buf, false);
      onData(ctx, buf);
   }

   private static boolean matches(ByteBuf buf, int bufOffset, AsciiString string) {
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

   private static int readDecNumber(ByteBuf buf, int index) {
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

   private static int skipWhitespaces(ByteBuf buf, int index) {
      final int i = buf.forEachByte(index, buf.writerIndex() - index, ByteProcessor.FIND_NON_LINEAR_WHITESPACE);
      if (i != -1) {
         return i;
      }
      return buf.writerIndex();
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

   @Override
   protected void onData(ChannelHandlerContext ctx, ByteBuf buf) {
      // noop - do not send to upper layers
      buf.release();
   }

   @Override
   protected void onStatus(int status) {
      HttpRequest request = connection.peekRequest(0);
      if (request == null) {
         if (HttpResponseStatus.REQUEST_TIMEOUT.code() == status) {
            // HAProxy sends 408 when we allocate the connection but do not use it within 10 seconds.
            log.debug("Closing connection {} as server timed out waiting for our first request.", connection);
         } else {
            log.error("Received unsolicited response (status {}) on {}", status, connection);
         }
         return;
      }
      if (request.isCompleted()) {
         log.trace("Request on connection {} has been already completed (error in handlers?), ignoring", connection);
      } else {
         HttpResponseHandlers handlers = request.handlers();
         request.enter();
         try {
            handlers.handleStatus(request, status, null); // TODO parse reason
         } finally {
            request.exit();
         }
         request.session.proceed();
      }
   }

   @Override
   protected void onHeaderRead(ByteBuf buf, int startOfName, int endOfName, int startOfValue, int endOfValue) {
      HttpRequest request = connection.peekRequest(0);
      if (request == null) {
         if (trace) {
            AsciiString name = Util.toAsciiString(buf, startOfName, endOfName - startOfName);
            String value = Util.toString(buf, startOfValue, endOfValue - startOfValue);
            log.trace("No request, received headers: {}: {}", name, value);
         }
      } else if (request.isCompleted()) {
         log.trace("Request on connection {} has been already completed (error in handlers?), ignoring", connection);
      } else {
         HttpResponseHandlers handlers = request.handlers();
         request.enter();
         try {
            AsciiString name = Util.toAsciiString(buf, startOfName, endOfName - startOfName);
            final int valueLen = endOfValue - startOfValue;
            // HTTP 1.1 RFC admit just latin header values, but still; better be safe and check it
            final CharSequence value;
            // Java Compact strings already perform this check, why doing it again?
            // AsciiString allocate the backed byte[] just once, saving to create/cache
            // a tmp one just to read buf content, if it won't be backed by a byte[] as well.
            if (Util.isAscii(buf, startOfValue, valueLen)) {
               value = Util.toAsciiString(buf, startOfValue, valueLen);
            } else {
               // the built-in method has the advantage vs Util.toString that the backing byte[] is cached, if ever happen
               value = buf.toString(startOfName, valueLen, CharsetUtil.UTF_8);
            }
            handlers.handleHeader(request, name, value);
         } finally {
            request.exit();
         }
         request.session.proceed();
      }
   }

   @Override
   protected void onBodyPart(ByteBuf buf, int startOffset, int length, boolean isLastPart) {
      if (length < 0 || length == 0 && !isLastPart) {
         return;
      }
      HttpRequest request = connection.peekRequest(0);
      // When previous handlers throw an error the request is already completed
      if (request != null && !request.isCompleted()) {
         HttpResponseHandlers handlers = request.handlers();
         request.enter();
         try {
            handlers.handleBodyPart(request, buf, startOffset, length, isLastPart);
         } finally {
            request.exit();
         }
         request.session.proceed();
      }
   }

   @Override
   protected void onCompletion(HttpRequest request) {
      boolean removed = false;
      // When previous handlers throw an error the request is already completed
      if (!request.isCompleted()) {
         request.enter();
         try {
            request.handlers().handleEnd(request, true);
            if (trace) {
               log.trace("Completed response on {}", this);
            }
         } catch (SessionStopException e) {
            if (connection.removeRequest(0, request)) {
               ((Http1xConnection) connection).releasePoolAndPulse();
            }
            throw e;
         } finally {
            request.exit();
         }
         removed = connection.removeRequest(0, request);
         request.session.proceed();
      }
      assert request.isCompleted();
      request.release();
      if (trace) {
         log.trace("Releasing request");
      }
      if (removed) {
         ((Http1xConnection) connection).releasePoolAndPulse();
      }
   }
}
