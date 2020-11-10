package io.hyperfoil.core.handlers.html;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class EmbeddedResourceProcessor extends Processor.BaseDelegating {
   private static final Logger log = LoggerFactory.getLogger(EmbeddedResourceProcessor.class);
   private static final boolean trace = log.isTraceEnabled();
   private static final byte[] HTTP_PREFIX = "http".getBytes(StandardCharsets.UTF_8);

   private final boolean ignoreExternal;

   EmbeddedResourceProcessor(boolean ignoreExternal, Processor delegate) {
      super(delegate);
      this.ignoreExternal = ignoreExternal;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      // TODO: here we should normalize the URL, remove escapes etc...

      boolean isAbsolute = hasPrefix(data, offset, length, HTTP_PREFIX);
      if (isAbsolute) {
         if (ignoreExternal) {
            int authorityStart = indexOf(data, offset, length, ':') + 3;
            boolean external = true;
            for (byte[] authority : session.httpDestinations().authorityBytes()) {
               if (hasPrefix(data, offset + authorityStart, length, authority)) {
                  external = false;
                  break;
               }
            }
            if (external) {
               if (trace) {
                  log.trace("#{} Ignoring external URL {}", session.uniqueId(), Util.toString(data, offset, length));
               }
               return;
            }
         }
         if (trace) {
            log.trace("#{} Matched URL {}", session.uniqueId(), Util.toString(data, offset, length));
         }
         delegate.process(session, data, offset, length, true);
      } else if (data.getByte(offset) == '/') {
         // No need to rewrite relative URL
         if (trace) {
            log.trace("#{} Matched URL {}", session.uniqueId(), Util.toString(data, offset, length));
         }
         delegate.process(session, data, offset, length, true);
      } else {
         HttpRequest request = (HttpRequest) session.currentRequest();
         ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(request.path.length() + length);
         Util.string2byteBuf(request.path, buffer);
         for (int i = buffer.writerIndex() - 1; i >= 0; --i) {
            if (buffer.getByte(i) == '/') {
               buffer.writerIndex(i + 1);
               break;
            }
         }
         buffer.ensureWritable(length);
         buffer.writeBytes(data, offset, length);
         if (trace) {
            log.trace("#{} Rewritten relative URL to {}", session.uniqueId(), Util.toString(buffer, buffer.readerIndex(), buffer.readableBytes()));
         }
         delegate.process(session, buffer, buffer.readerIndex(), buffer.readableBytes(), true);
         buffer.release();
      }
   }

   private int indexOf(ByteBuf data, int offset, int length, char c) {
      for (int i = 0; i <= length; ++i) {
         if (data.getByte(offset + i) == c) {
            return i;
         }
      }
      return -1;
   }

   private boolean hasPrefix(ByteBuf data, int offset, int length, byte[] authority) {
      int i = 0;
      for (; i < authority.length && i < length; i++) {
         if (data.getByte(offset + i) != authority[i]) {
            return false;
         }
      }
      return i == authority.length;
   }
}
