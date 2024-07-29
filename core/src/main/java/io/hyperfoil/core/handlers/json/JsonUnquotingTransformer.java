package io.hyperfoil.core.handlers.json;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class JsonUnquotingTransformer
      implements Transformer, Processor, ResourceUtilizer, Session.ResourceKey<JsonUnquotingTransformer.Context> {
   private static final ByteBuf NEWLINE = Unpooled.wrappedBuffer("\n".getBytes(StandardCharsets.UTF_8));
   private static final ByteBuf BACKSPACE = Unpooled.wrappedBuffer("\b".getBytes(StandardCharsets.UTF_8));
   private static final ByteBuf FORMFEED = Unpooled.wrappedBuffer("\f".getBytes(StandardCharsets.UTF_8));
   private static final ByteBuf CR = Unpooled.wrappedBuffer("\r".getBytes(StandardCharsets.UTF_8));
   private static final ByteBuf TAB = Unpooled.wrappedBuffer("\t".getBytes(StandardCharsets.UTF_8));

   protected final Transformer delegate;

   public JsonUnquotingTransformer(Transformer delegate) {
      this.delegate = delegate;
   }

   public JsonUnquotingTransformer(Processor delegate) {
      this(new ProcessorAdapter(delegate));
   }

   @Override
   public void before(Session session) {
      delegate.before(session);
      Context context = session.getResource(this);
      context.reset();
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      transform(session, data, offset, length, isLastPart, null);
   }

   @Override
   public void transform(Session session, ByteBuf input, int offset, int length, boolean isLastFragment, ByteBuf output) {
      Context context = session.getResource(this);
      int begin;
      if (context.unicode) {
         begin = offset + processUnicode(session, input, offset, length, isLastFragment, output, context, 0);
      } else if (context.first && input.getByte(offset) == '"') {
         begin = offset + 1;
      } else {
         begin = offset;
      }

      for (int i = begin - offset; i < length; ++i) {
         if (context.escaped || input.getByte(offset + i) == '\\') {
            int fragmentLength = offset + i - begin;
            if (fragmentLength > 0) {
               delegate.transform(session, input, begin, fragmentLength, isLastFragment && fragmentLength == length, output);
            }
            if (context.escaped) {
               // This happens when we're starting with chunk escaped in previous chunk
               context.escaped = false;
            } else {
               ++i;
               if (i >= length) {
                  context.escaped = true;
                  begin = offset + i;
                  break;
               }
            }

            switch (input.getByte(offset + i)) {
               case 'n':
                  delegate.transform(session, NEWLINE, 0, NEWLINE.readableBytes(), isLastFragment && i == length - 1, output);
                  begin = offset + i + 1;
                  break;
               case 'b':
                  delegate.transform(session, BACKSPACE, 0, BACKSPACE.readableBytes(), isLastFragment && i == length - 1,
                        output);
                  begin = offset + i + 1;
                  break;
               case 'f':
                  delegate.transform(session, FORMFEED, 0, FORMFEED.readableBytes(), isLastFragment && i == length - 1, output);
                  begin = offset + i + 1;
                  break;
               case 'r':
                  delegate.transform(session, CR, 0, CR.readableBytes(), isLastFragment && i == length - 1, output);
                  begin = offset + i + 1;
                  break;
               case 't':
                  delegate.transform(session, TAB, 0, TAB.readableBytes(), isLastFragment && i == length - 1, output);
                  begin = offset + i + 1;
                  break;
               case 'u':
                  context.unicode = true;
                  ++i;
                  i = processUnicode(session, input, offset, length, isLastFragment, output, context, i) - 1;
                  begin = offset + i + 1;
                  break;
               default:
                  // unknown escaped char
                  assert false;
               case '"':
               case '/':
               case '\\':
                  // just skip the escape
                  begin = offset + i;
            }
         } else if (isLastFragment && i == length - 1 && input.getByte(offset + i) == '"') {
            --length;
         }
      }
      context.first = false;
      int fragmentLength = length - begin + offset;
      // we need to send this even if the length == 0 as when we have removed the last quote
      // the previous chunk had not isLastFragment==true
      delegate.transform(session, input, begin, fragmentLength, isLastFragment, output);
      if (isLastFragment) {
         context.reset();
      }
   }

   private int processUnicode(Session session, ByteBuf data, int offset, int length, boolean isLastPart, ByteBuf output,
         Context context, int i) {
      while (i < length && context.unicodeDigits < 4) {
         context.unicodeChar = context.unicodeChar * 16;
         byte b = data.getByte(offset + i);
         if (b >= '0' && b <= '9') {
            context.unicodeChar += b - '0';
         } else if (b >= 'a' && b <= 'f') {
            context.unicodeChar += 10 + b - 'a';
         } else if (b >= 'A' && b <= 'F') {
            context.unicodeChar += 10 + b - 'A';
         } else {
            // ignore parsing errors as 0 in runtime
            assert false;
         }
         context.unicodeDigits++;
         ++i;
      }
      if (context.unicodeDigits == 4) {
         // TODO: allocation, probably inefficient...
         ByteBuf utf8 = Unpooled.wrappedBuffer(Character.toString((char) context.unicodeChar).getBytes(StandardCharsets.UTF_8));
         delegate.transform(session, utf8, utf8.readerIndex(), utf8.readableBytes(), isLastPart && i == length - 1, output);
         context.unicode = false;
         context.unicodeChar = 0;
         context.unicodeDigits = 0;
      }
      return i;
   }

   @Override
   public void after(Session session) {
      delegate.after(session);
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, Context::new);
   }

   public static class Context implements Session.Resource {
      private int unicodeDigits;
      private int unicodeChar;
      private boolean first = true;
      private boolean escaped;
      private boolean unicode;

      private void reset() {
         first = true;
         escaped = false;
         unicode = false;
         unicodeDigits = 0;
         unicodeChar = 0;
      }
   }

}
