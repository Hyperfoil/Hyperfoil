package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.Processor;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

/**
 * Simple pattern (no regexp) search based on Rabin-Karp algorithm.
 * Does not handle the intricacies of UTF-8 mapping same strings to different bytes.
 */
public class SearchHandler implements Processor<Request>, ResourceUtilizer, Session.ResourceKey<SearchHandler.Context> {
   private final byte[] begin, end;
   private final int beginHash, endHash;
   private final int beginCoef, endCoef;
   private Processor<Request> processor;

   public SearchHandler(String begin, String end, Processor<Request> processor) {
      this.begin = begin.getBytes(StandardCharsets.UTF_8);
      this.end = end.getBytes(StandardCharsets.UTF_8);
      this.beginHash = BaseSearchContext.computeHash(this.begin);
      this.beginCoef = BaseSearchContext.computeCoef(this.begin.length);
      this.endHash = BaseSearchContext.computeHash(this.end);
      this.endCoef = BaseSearchContext.computeCoef(this.end.length);
      this.processor = processor;
   }

   @Override
   public void before(Request request) {
      Context ctx = request.session.getResource(this);
      ctx.reset();
      processor.before(request);
   }

   @Override
   public void process(Request request, ByteBuf data, final int offset, int length, boolean isLast) {
      Context ctx = request.session.getResource(this);
      ctx.add(data, offset, length);
      int endIndex = offset + length;
      int index = ctx.initHash(offset, ctx.lookupText.length);
      while (ctx.test(index)) {
         if (ctx.lookupText == end) {
            fireProcessor(ctx, request, index);
         } else {
            ctx.mark(index);
         }
         ctx.swap();
         index = ctx.initHash(index, ctx.lookupText.length);
      }
      while (index < endIndex) {
         ctx.advance(data.getByte(index++), ctx.lookupCoef, index, ctx.lookupText.length + 1);
         while (ctx.test(index)) {
            if (ctx.lookupText == end) {
               fireProcessor(ctx, request, index);
            } else {
               ctx.mark(index);
            }
            ctx.swap();
            index = ctx.initHash(index, ctx.lookupText.length);
         }
      }
   }

   private void fireProcessor(Context ctx, Request request, int index) {
      int endPart = ctx.currentPart;
      int endPos = index - end.length;
      while (endPos < 0) {
         endPart--;
         if (endPart < 0) {
            endPart = 0;
            endPos = 0;
         } else {
            endPos += ctx.endIndices[endPart];
         }
      }
      while (ctx.markPart < endPart) {
         ByteBuf data = ctx.parts[ctx.markPart];
         // if the begin ends with part, we'll skip the 0-length process call
         int length = ctx.endIndices[ctx.markPart] - ctx.markPos;
         if (length > 0) {
            processor.process(request, data, ctx.markPos, length, false);
         }
         ctx.markPos = 0;
         ctx.markPart++;
      }
      processor.process(request, ctx.parts[endPart], ctx.markPos, endPos - ctx.markPos, true);
   }

   @Override
   public void after(Request request) {
      Context ctx = request.session.getResource(this);
      // release buffers
      ctx.reset();
      processor.after(request);
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
   }

   class Context extends BaseSearchContext {
      byte[] lookupText;
      int lookupHash;
      int lookupCoef;
      int markPart = -1;
      int markPos = -1;

      void swap() {
         currentHash = 0;
         hashedBytes = 0;
         if (lookupText == end) {
            lookupText = begin;
            lookupHash = beginHash;
            lookupCoef = beginCoef;
         } else {
            lookupText = end;
            lookupHash = endHash;
            lookupCoef = endCoef;
         }
      }

      boolean test(int index) {
         if (currentHash == lookupHash) {
            for (int i = 0; i < lookupText.length; ++i) {
               if (lookupText[i] != byteRelative(index, lookupText.length - i)) {
                  return false;
               }
            }
            return true;
         }
         return false;
      }

      @Override
      void shiftParts() {
         super.shiftParts();
         --markPart;
         if (markPart < 0) {
            markPart = 0;
            markPos = 0;
         }
      }

      @Override
      void reset() {
         super.reset();
         lookupText = begin;
         lookupHash = beginHash;
         lookupCoef = beginCoef;
         markPart = -1;
         markPos = -1;
      }

      void mark(int index) {
         markPart = currentPart;
         markPos = index;
      }
   }
}
