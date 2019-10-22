package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

import io.hyperfoil.api.connection.Processor;
import io.hyperfoil.api.connection.Request;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

/**
 * Simple pattern (no regexp) search based on Rabin-Karp algorithm.
 * Does not handle the intricacies of UTF-8 mapping same strings to different bytes.
 */
public class SearchValidator implements Processor<Request>, ResourceUtilizer, Session.ResourceKey<SearchValidator.Context> {
   private final byte[] text;
   private final int hash;
   private final int coef;
   private final IntPredicate match;

   /**
    * @param text  Search pattern.
    * @param match Expected number of matches.
    */
   public SearchValidator(String text, IntPredicate match) {
      this.text = text.getBytes(StandardCharsets.UTF_8);
      this.match = match;
      this.hash = BaseSearchContext.computeHash(this.text);
      this.coef = BaseSearchContext.computeCoef(this.text.length);
   }

   @Override
   public void process(Request request, ByteBuf data, final int offset, int length, boolean isLastPart) {
      Context ctx = request.session.getResource(this);
      ctx.add(data, offset, length);
      int endIndex = offset + length;
      int index = ctx.initHash(offset, text.length);
      index = test(ctx, index);
      while (index < endIndex) {
         ctx.advance(data.getByte(index++), coef, index, text.length + 1);
         index = test(ctx, index);
      }
   }

   private int test(Context ctx, int index) {
      if (ctx.currentHash == hash) {
         for (int i = 0; i < text.length; ++i) {
            if (text[i] != ctx.byteRelative(index, text.length - i)) {
               return i;
            }
         }
         ctx.matches++;
         ctx.currentHash = 0;
         ctx.hashedBytes = 0;
         return ctx.initHash(index, text.length);
      }
      return index;
   }

   @Override
   public void before(Request request) {
      Context ctx = request.session.getResource(this);
      ctx.reset();
   }

   @Override
   public void after(Request request) {
      Context ctx = request.session.getResource(this);
      boolean match = this.match.test(ctx.matches);
      ctx.reset();
      if (!match) {
         request.markInvalid();
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
   }

   static class Context extends BaseSearchContext {
      int matches;

      @Override
      public void reset() {
         super.reset();
         matches = 0;
      }
   }
}
