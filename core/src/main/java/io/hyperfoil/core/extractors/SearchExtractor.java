package io.hyperfoil.core.extractors;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.BodyExtractor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

/**
 * Simple pattern (no regexp) search based on Rabin-Karp algorithm.
 * Does not handle the intricacies of UTF-8 mapping same strings to different bytes.
 */
public class SearchExtractor implements BodyExtractor, ResourceUtilizer, Session.ResourceKey<SearchExtractor.Context> {
   private final byte[] begin, end;
   private final int beginHash, endHash;
   private final int beginCoef, endCoef;
   private Processor<Request> processor;

   public SearchExtractor(String begin, String end, Processor<Request> processor) {
      this.begin = begin.getBytes(StandardCharsets.UTF_8);
      this.end = end.getBytes(StandardCharsets.UTF_8);
      this.beginHash = computeHash(this.begin);
      this.beginCoef = intPow(31, this.begin.length);
      this.endHash = computeHash(this.end);
      this.endCoef = intPow(31, this.end.length);
      this.processor = processor;
   }

   private int intPow(int base, int exp) {
      int value = 1;
      for (int i = exp; i > 0; --i) {
         value *= base;
      }
      return value;
   }

   private int computeHash(byte[] bytes) {
      int hash = 0;
      for (int b : bytes) {
         hash = 31 * hash + b;
      }
      return hash;
   }

   @Override
   public void beforeData(HttpRequest request) {
      Context ctx = request.session.getResource(this);
      ctx.reset();
      processor.before(request);
   }

   @Override
   public void extractData(HttpRequest request, ByteBuf data) {
      Context ctx = request.session.getResource(this);
      ctx.add(data);
      initHash(ctx, data);
      while (test(ctx)) {
         if (ctx.lookupText == end) {
            fireProcessor(ctx, request);
         } else {
            ctx.mark();
         }
         swap(ctx, data);
      }
      while (data.isReadable()) {
         ctx.currentHash = 31 * ctx.currentHash + data.readByte();
         ctx.currentHash -= ctx.lookupCoef * ctx.byteRelative(ctx.lookupText.length + 1);
         while (test(ctx)) {
            if (ctx.lookupText == end) {
               fireProcessor(ctx, request);
            } else {
               ctx.mark();
            }
            swap(ctx, data);
         }
      }
   }

   private void fireProcessor(Context ctx, HttpRequest request) {
      int endPart = ctx.currentPart;
      int endPos = ctx.parts[endPart].readerIndex() - end.length;
      while (endPos < 0) {
         endPart--;
         if (endPart < 0) {
            endPart = 0;
            endPos = 0;
         } else {
            endPos += ctx.parts[endPart].writerIndex();
         }
      }
      while (ctx.markPart < endPart) {
         ByteBuf data = ctx.parts[ctx.markPart];
         // if the begin ends with part, we'll skip the 0-length process call
         if (ctx.markPos != data.writerIndex()) {
            processor.process(request, data, ctx.markPos, data.writerIndex() - ctx.markPos, false);
         }
         ctx.markPos = 0;
         ctx.markPart++;
      }
      processor.process(request, ctx.parts[endPart], ctx.markPos, endPos - ctx.markPos, true);
   }

   private boolean test(Context ctx) {
      if (ctx.currentHash == ctx.lookupHash) {
         for (int i = 0; i < ctx.lookupText.length; ++i) {
            if (ctx.lookupText[i] != ctx.byteRelative(ctx.lookupText.length - i)) {
               return false;
            }
         }
         return true;
      }
      return false;
   }

   private void swap(Context ctx, ByteBuf data) {
      ctx.currentHash = 0;
      ctx.hashedBytes = 0;
      if (ctx.lookupText == end) {
         ctx.lookupText = begin;
         ctx.lookupHash = beginHash;
         ctx.lookupCoef = beginCoef;
      } else {
         ctx.lookupText = end;
         ctx.lookupHash = endHash;
         ctx.lookupCoef = endCoef;
      }
      initHash(ctx, data);
   }

   private void initHash(Context ctx, ByteBuf data) {
      while (data.isReadable() && ctx.hashedBytes < ctx.lookupText.length) {
         ctx.currentHash = 31 * ctx.currentHash + data.readByte();
         ++ctx.hashedBytes;
      }
   }

   @Override
   public void afterData(HttpRequest request) {
      Context ctx = request.session.getResource(this);
      // release buffers
      ctx.reset();
      processor.after(request);
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
   }

   class Context implements Session.Resource {
      int hashedBytes;
      int currentHash;
      byte[] lookupText;
      int lookupHash;
      int lookupCoef;
      int markPart = -1;
      int markPos = -1;

      ByteBuf[] parts = new ByteBuf[16];
      int currentPart = -1;

      void add(ByteBuf data) {
         ++currentPart;
         if (currentPart >= parts.length) {
            parts[0].release();
            System.arraycopy(parts, 1, parts, 0, parts.length - 1);
            --currentPart;
            --markPart;
            if (markPart < 0) {
               markPart = 0;
               markPos = 0;
            }
         }
         parts[currentPart] = data.retain();
      }

      int byteRelative(int offset) {
         int part = currentPart;
         while (parts[part].readerIndex() - offset < 0) {
            offset -= parts[part].readerIndex();
            --part;
         }
         return parts[part].getByte(parts[part].readerIndex() - offset);
      }

      void reset() {
         lookupText = begin;
         lookupHash = beginHash;
         lookupCoef = beginCoef;
         hashedBytes = 0;
         currentHash = 0;
         markPart = -1;
         markPos = -1;
         currentPart = -1;
         for (int i = 0; i < parts.length; ++i) {
            if (parts[i] != null) {
               parts[i].release();
               parts[i] = null;
            }
         }
      }

      void mark() {
         markPart = currentPart;
         markPos = parts[currentPart].readerIndex();
      }
   }
}
