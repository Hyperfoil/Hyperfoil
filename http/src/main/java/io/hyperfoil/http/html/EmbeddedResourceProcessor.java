package io.hyperfoil.http.html;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.impl.Util;
import io.hyperfoil.http.HttpUtil;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.api.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.internal.AppendableCharSequence;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class EmbeddedResourceProcessor extends Processor.BaseDelegating {
   private static final Logger log = LogManager.getLogger(EmbeddedResourceProcessor.class);
   private static final boolean trace = log.isTraceEnabled();
   private static final byte[] HTTP_PREFIX = HttpUtil.HTTP_PREFIX.getBytes(StandardCharsets.UTF_8);
   private static final byte[] HTTPS_PREFIX = HttpUtil.HTTP_PREFIX.getBytes(StandardCharsets.UTF_8);

   private final boolean ignoreExternal;
   private final FetchResourceHandler fetchResource;

   EmbeddedResourceProcessor(boolean ignoreExternal, Processor delegate, FetchResourceHandler fetchResource) {
      super(delegate);
      this.ignoreExternal = ignoreExternal;
      this.fetchResource = fetchResource;
   }

   @Override
   public void before(Session session) {
      if (fetchResource != null) {
         fetchResource.before(session);
      }
      if (delegate != null) {
         super.before(session);
      }
   }

   @Override
   public void after(Session session) {
      if (delegate != null) {
         super.after(session);
      }
      if (fetchResource != null) {
         fetchResource.after(session);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      // TODO: here we should normalize the URL, remove escapes etc...

      HttpRequest request = HttpRequest.ensure(session.currentRequest());
      if (request == null) {
         return;
      }

      boolean isHttp = Util.hasPrefix(data, offset, length, HTTP_PREFIX);
      boolean isHttps = Util.hasPrefix(data, offset, length, HTTPS_PREFIX);
      if (isHttp || isHttps) {
         String authority = null;
         HttpDestinationTable destinations = HttpDestinationTable.get(session);
         byte[][] authorityBytes = destinations.authorityBytes();
         for (int i = 0; i < authorityBytes.length; i++) {
            if (HttpUtil.authorityMatch(data, offset, length, authorityBytes[i], isHttp)) {
               authority = destinations.authorities()[i];
               break;
            }
         }
         if (authority == null && ignoreExternal) {
            if (trace) {
               log.trace("#{} Ignoring external URL {}", session.uniqueId(), Util.toString(data, offset, length));
            }
            return;
         }
         if (trace) {
            log.trace("#{} Matched URL {}", session.uniqueId(), Util.toString(data, offset, length));
         }
         if (fetchResource != null) {
            int pathStart = data.indexOf(offset + (isHttp ? HTTP_PREFIX.length : HTTPS_PREFIX.length), offset + length, (byte) '/');
            CharSequence path = pathStart < 0 ? "/" : data.toString(pathStart, offset + length - pathStart, StandardCharsets.UTF_8);
            fetchResource.handle(session, authority, path);
         }
         if (delegate != null) {
            delegate.process(session, data, offset, length, true);
         }
      } else if (data.getByte(offset) == '/') {
         // No need to rewrite relative URL
         if (trace) {
            log.trace("#{} Matched URL {}", session.uniqueId(), Util.toString(data, offset, length));
         }
         if (fetchResource != null) {
            fetchResource.handle(session, request.authority, data.toString(offset, length, StandardCharsets.UTF_8));
         }
         if (delegate != null) {
            delegate.process(session, data, offset, length, true);
         }
      } else {
         if (trace) {
            log.trace("#{} Matched URL {}", session.uniqueId(), Util.toString(data, offset, length));
         }
         if (fetchResource != null) {
            AppendableCharSequence newPath = new AppendableCharSequence(request.path.length() + length);
            int end = request.path.lastIndexOf('/');
            if (end < 0) {
               newPath.append(request.path).append('/');
            } else {
               newPath.append(request.path, 0, end + 1);
            }
            // TODO allocation
            newPath.append(Util.toString(data, offset, length));
            if (trace) {
               log.trace("#{} Rewritten relative URL to {}", session.uniqueId(), newPath);
            }
            fetchResource.handle(session, request.authority, newPath);
         }
         if (delegate != null) {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(request.path.length() + length);
            try {
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
            } finally {
               buffer.release();
            }
         }
      }
   }

}
