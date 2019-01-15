package io.hyperfoil.core.validators;

import java.nio.charset.StandardCharsets;
import java.util.function.IntPredicate;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.api.http.BodyValidator;

/**
 * Simple pattern (no regexp) search based on Rabin-Karp algorithm.
 * Does not handle the intricacies of UTF-8 mapping same strings to different bytes.
 */
public class SearchValidator implements BodyValidator, ResourceUtilizer, Session.ResourceKey<SearchValidator.Context> {
   private final byte[] text;
   private final int hash;
   private final int coef;
   private final IntPredicate match;

   /**
    * @param text Search pattern.
    */
   public SearchValidator(String text, IntPredicate match) {
      this.text = text.getBytes(StandardCharsets.UTF_8);
      this.match = match;
      int hash = 0;
      int coef = 1;
      for (int b : this.text) {
         hash = 31 * hash + b;
         coef *= 31;
      }
      this.hash = hash;
      this.coef = coef;
   }

   @Override
   public void validateData(Request request, ByteBuf data) {
      Context ctx = request.session.getResource(this);
      ctx.add(data);
      initHash(ctx, data);
      test(ctx, data);
      while (data.isReadable()) {
         ctx.currentHash = 31 * ctx.currentHash + data.readByte();
         ctx.currentHash -= coef * ctx.byteRelative(text.length + 1);
         test(ctx, data);
      }
   }

   private void initHash(Context ctx, ByteBuf data) {
      while (data.isReadable() && ctx.hashedBytes < text.length) {
         ctx.currentHash = 31 * ctx.currentHash + data.readByte();
         ++ctx.hashedBytes;
      }
   }

   private void test(Context ctx, ByteBuf data) {
      if (ctx.currentHash == hash) {
         for (int i = 0; i < text.length; ++i) {
            if (text[i] != ctx.byteRelative(text.length - i)) {
               return;
            }
         }
         ctx.matches++;
         ctx.currentHash = 0;
         ctx.hashedBytes = 0;
         initHash(ctx, data);
      }
   }

   @Override
   public void beforeData(Request request) {
      Context ctx = request.session.getResource(this);
      ctx.reset();
   }

   @Override
   public boolean validate(Request request) {
      Context ctx = request.session.getResource(this);
      boolean match = this.match.test(ctx.matches);
      ctx.reset();
      return match;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
   }

   class Context implements Session.Resource {
      int hashedBytes;
      int currentHash;
      int matches;

      ByteBuf[] parts = new ByteBuf[16];
      int currentPart = -1;

      public void add(ByteBuf data) {
         ++currentPart;
         if (currentPart >= parts.length) {
            parts[0].release();
            System.arraycopy(parts, 1, parts, 0, parts.length - 1);
            --currentPart;
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

      public void reset() {
         hashedBytes = 0;
         currentHash = 0;
         currentPart = -1;
         for (int i = 0; i < parts.length; ++i) {
            if (parts[i] != null) {
               parts[i].release();
               parts[i] = null;
            }
         }
         matches = 0;
      }
   }
}
