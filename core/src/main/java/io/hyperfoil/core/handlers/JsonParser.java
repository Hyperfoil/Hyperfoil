package io.hyperfoil.core.handlers;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public abstract class JsonParser<S> implements Serializable {
   protected static final int MAX_PARTS = 16;

   protected final String query;
   private final JsonParser.Selector[] selectors;

   public JsonParser(String query) {
      this.query = query;

      byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);
      if (queryBytes.length == 0 || queryBytes[0] != '.') {
         throw new BenchmarkDefinitionException("Path should start with '.'");
      }
      ArrayList<Selector> selectors = new ArrayList<>();
      int next = 1;
      for (int i = 1; i < queryBytes.length; ++i) {
         if (queryBytes[i] == '[' || queryBytes[i] == '.' && next < i) {
            while (queryBytes[next] == '.') ++next;
            if (next != i) {
               selectors.add(new AttribSelector(Arrays.copyOfRange(queryBytes, next, i)));
            }
            next = i + 1;
         }
         if (queryBytes[i] == '[') {
            ArraySelector arraySelector = new ArraySelector();
            ++i;
            int startIndex = i, endIndex = i;
            for (; i < queryBytes.length; ++i) {
               if (queryBytes[i] == ']') {
                  if (endIndex < i) {
                     arraySelector.rangeEnd = bytesToInt(queryBytes, startIndex, i);
                     if (startIndex == endIndex) {
                        arraySelector.rangeStart = arraySelector.rangeEnd;
                     }
                  }
                  selectors.add(arraySelector);
                  next = i + 1;
                  break;
               } else if (queryBytes[i] == ':') {
                  if (startIndex < i) {
                     arraySelector.rangeStart = bytesToInt(queryBytes, startIndex, i);
                  }
                  endIndex = i + 1;
               }
            }
         }
      }
      if (next < queryBytes.length) {
         while (queryBytes[next] == '.') ++next;
         selectors.add(new AttribSelector(Arrays.copyOfRange(queryBytes, next, queryBytes.length)));
      }
      this.selectors = selectors.toArray(new JsonParser.Selector[0]);
   }

   protected abstract void fireMatch(Context context, S source, ByteStream data, int offset, int length, boolean isLastPart);

   private static int bytesToInt(byte[] bytes, int start, int end) {
      int value = 0;
      for (; ; ) {
         if (bytes[start] < '0' || bytes[start] > '9') {
            throw new BenchmarkDefinitionException("Invalid range specification: " + new String(bytes));
         }
         value += bytes[start] - '0';
         if (++start >= end) {
            return value;
         } else {
            value *= 10;
         }
      }
   }

   interface Selector extends Serializable {
      Context newContext();

      interface Context {
         void reset();
      }
   }

   private static class AttribSelector implements JsonParser.Selector {
      byte[] name;

      AttribSelector(byte[] name) {
         this.name = name;
      }

      boolean match(ByteStream data, int start, int end, int offset) {
         assert start <= end;
         for (int i = 0; i < name.length && i < end - start; ++i) {
            if (name[i + offset] != data.getByte(start + i)) return false;
         }
         return true;
      }

      @Override
      public Context newContext() {
         return null;
      }
   }

   private static class ArraySelector implements Selector {
      int rangeStart = 0;
      int rangeEnd = Integer.MAX_VALUE;

      @Override
      public Context newContext() {
         return new ArraySelectorContext();
      }

      boolean matches(ArraySelectorContext context) {
         return context.active && context.currentItem >= rangeStart && context.currentItem <= rangeEnd;
      }
   }

   private static class ArraySelectorContext implements Selector.Context {
      boolean active;
      int currentItem;

      @Override
      public void reset() {
         active = false;
         currentItem = 0;
      }
   }

   protected class Context implements Session.Resource {
      Selector.Context[] selectorContext = new Selector.Context[selectors.length];
      int level;
      int selectorLevel;
      int selector;
      boolean inQuote;
      boolean inAttrib;
      boolean escaped;
      int attribStartPart;
      int attribStartIndex;
      int lastCharPart;
      int lastCharIndex;
      int valueStartPart;
      int valueStartIndex;
      ByteStream[] parts = new ByteStream[MAX_PARTS];
      int nextPart;
      ByteStream[] pool = new ByteStream[MAX_PARTS];

      protected Context(Function<Context, ByteStream> byteStreamSupplier) {
         for (int i = 0; i < pool.length; ++i) {
            pool[i] = byteStreamSupplier.apply(this);
         }
         for (int i = 0; i < selectors.length; ++i) {
            selectorContext[i] = selectors[i].newContext();
         }
         reset();
      }

      public void reset() {
         for (Selector.Context ctx : selectorContext) {
            if (ctx != null) ctx.reset();
         }
         level = -1;
         selectorLevel = 0;
         selector = 0;
         inQuote = false;
         inAttrib = false;
         escaped = false;
         attribStartPart = -1;
         attribStartIndex = -1;
         lastCharPart = -1;
         lastCharIndex = -1;
         valueStartPart = -1;
         valueStartIndex = -1;
         nextPart = 0;
         for (int i = 0; i < parts.length; ++i) {
            if (parts[i] == null) break;
            parts[i].release();
            parts[i] = null;
         }
      }

      private Selector.Context current() {
         return selectorContext[selector];
      }

      public void parse(ByteStream data, S source) {
         while (data.isReadable()) {
            byte b = data.readByte();
            switch (b) {
               case ' ':
               case '\n':
               case '\t':
               case '\r':
                  // ignore whitespace
                  break;
               case '\\':
                  escaped = !escaped;
                  break;
               case '{':
                  if (!inQuote) {
                     ++level;
                     inAttrib = true;
                     // TODO assert we have active attrib selector
                  }
                  break;
               case '}':
                  if (!inQuote) {
                     tryRecord(source, data);
                     if (level == selectorLevel) {
                        --selectorLevel;
                        --selector;
                     }
                     --level;
                  }
                  break;
               case '"':
                  if (!escaped) {
                     inQuote = !inQuote;
                  }
                  break;
               case ':':
                  if (!inQuote) {
                     if (selectorLevel == level && attribStartIndex >= 0 && selector < selectors.length && selectors[selector] instanceof AttribSelector) {
                        AttribSelector selector = (AttribSelector) selectors[this.selector];
                        int offset = 0;
                        boolean previousPartsMatch = true;
                        if (attribStartPart >= 0) {
                           int endIndex;
                           if (lastCharPart != attribStartPart) {
                              endIndex = parts[attribStartPart].writerIndex();
                           } else {
                              endIndex = lastCharIndex;
                           }
                           while (previousPartsMatch = selector.match(parts[attribStartPart], attribStartIndex, endIndex, offset)) {
                              offset += endIndex - attribStartIndex;
                              attribStartPart++;
                              attribStartIndex = 0;
                              if (attribStartPart >= parts.length || parts[attribStartPart] == null) {
                                 break;
                              }
                           }
                        }
                        if (previousPartsMatch && (lastCharPart >= 0 || selector.match(data, attribStartIndex, lastCharIndex, offset))) {
                           onMatch(data);
                        }
                     }
                     attribStartIndex = -1;
                     inAttrib = false;
                  }
                  break;
               case ',':
                  if (!inQuote) {
                     inAttrib = true;
                     attribStartIndex = -1;
                     tryRecord(source, data);
                     if (selectorLevel == level && selector < selectors.length && current() instanceof ArraySelectorContext) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        if (asc.active) {
                           asc.currentItem++;
                        }
                        if (((ArraySelector) selectors[selector]).matches(asc)) {
                           onMatch(data);
                        }
                     }
                  }
                  break;
               case '[':
                  if (!inQuote) {
                     ++level;
                     if (selectorLevel == level && selector < selectors.length && selectors[selector] instanceof ArraySelector) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        asc.active = true;
                        if (((ArraySelector) selectors[selector]).matches(asc)) {
                           onMatch(data);
                        }
                     }
                  }
                  break;
               case ']':
                  if (!inQuote) {
                     tryRecord(source, data);
                     if (selectorLevel == level && selector < selectors.length && current() instanceof ArraySelectorContext) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        asc.active = false;
                        --selectorLevel;
                     }
                     --level;
                  }
                  break;
               default:
                  lastCharPart = -1;
                  lastCharIndex = data.readerIndex();
                  if (inAttrib && attribStartIndex < 0) {
                     attribStartPart = -1;
                     attribStartIndex = data.readerIndex() - 1;
                  }
            }
            if (b != '\\') {
               escaped = false;
            }
         }
         if (attribStartIndex >= 0 || valueStartIndex >= 0) {
            if (nextPart == parts.length) {
               parts[0].release();
               System.arraycopy(parts, 1, parts, 0, parts.length - 1);
               --nextPart;
               if (attribStartPart == 0 && attribStartIndex >= 0) {
                  attribStartPart = 0;
                  attribStartIndex = 0;
               } else if (attribStartPart > 0) {
                  --attribStartPart;
               }
               if (lastCharPart == 0 && lastCharIndex >= 0) {
                  lastCharPart = 0;
                  lastCharIndex = 0;
               } else if (lastCharPart > 0) {
                  --lastCharPart;
               }
               if (valueStartPart == 0 && valueStartIndex >= 0) {
                  valueStartPart = 0;
                  valueStartIndex = 0;
               } else if (valueStartPart > 0) {
                  --valueStartPart;
               }
            }
            parts[nextPart] = data.retain();
            if (attribStartPart < 0) {
               attribStartPart = nextPart;
            }
            if (lastCharPart < 0) {
               lastCharPart = nextPart;
            }
            if (valueStartPart < 0) {
               valueStartPart = nextPart;
            }
            ++nextPart;
         } else {
            for (int i = 0; i < parts.length && parts[i] != null; ++i) {
               parts[i].release();
               parts[i] = null;
            }
            nextPart = 0;
         }
      }

      private void onMatch(ByteStream data) {
         ++selector;
         if (selector < selectors.length) {
            ++selectorLevel;
         } else {
            valueStartPart = -1;
            valueStartIndex = data.readerIndex();
         }
      }

      private void tryRecord(S source, ByteStream data) {
         if (selectorLevel == level && valueStartIndex >= 0) {
            // valueStartIndex is always before quotes here
            ByteStream buf = valueStartPart < 0 ? data : parts[valueStartPart];
            buf = tryAdvanceValueStart(data, buf);
            LOOP:
            while (valueStartIndex < buf.writerIndex() || valueStartPart != -1) {
               switch (buf.getByte(valueStartIndex)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     ++valueStartIndex;
                     buf = tryAdvanceValueStart(data, buf);
                     break;
                  default:
                     break LOOP;
               }
            }
            int end = data.readerIndex() - 1;
            int endPart = nextPart;
            buf = data;
            if (end == 0) {
               endPart--;
               buf = parts[endPart];
               end = buf.writerIndex();
            }
            LOOP:
            while (end > valueStartIndex || valueStartPart >= 0 && endPart > valueStartPart) {
               switch (buf.getByte(end - 1)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     --end;
                     if (end == 0) {
                        if (valueStartPart >= 0 && endPart > valueStartPart) {
                           endPart--;
                           buf = parts[endPart];
                           end = buf.writerIndex();
                        }
                     }
                     break;
                  default:
                     break LOOP;
               }
            }
            if (valueStartIndex == end && (valueStartPart < 0 || valueStartPart == endPart)) {
               // This happens when we try to select from a 0-length array
               // - as long as there are not quotes there's nothing to record.
               valueStartIndex = -1;
               --selector;
               return;
            }
            while (valueStartPart >= 0 && valueStartPart != endPart) {
               int valueEndIndex = parts[valueStartPart].writerIndex();
               fireMatch(this, source, parts[valueStartPart], valueStartIndex, valueEndIndex - valueStartIndex, false);
               incrementValueStartPart();
            }
            fireMatch(this, source, buf(data, endPart), valueStartIndex, end - valueStartIndex, true);
            valueStartIndex = -1;
            --selector;
         }
      }

      private ByteStream tryAdvanceValueStart(ByteStream data, ByteStream buf) {
         if (valueStartIndex >= buf.writerIndex() && valueStartPart >= 0) {
            valueStartPart++;
            if (valueStartPart >= parts.length || (buf = parts[valueStartPart]) == null) {
               buf = data;
               valueStartPart = -1;
            }
            valueStartIndex = 0;
         }
         return buf;
      }

      private void incrementValueStartPart() {
         valueStartIndex = 0;
         valueStartPart++;
         if (valueStartPart >= parts.length || parts[valueStartPart] == null) {
            valueStartPart = -1;
         }
      }

      private ByteStream buf(ByteStream data, int part) {
         if (part < 0 || part >= parts.length || parts[part] == null) {
            return data;
         }
         return parts[part];
      }

      public ByteStream retain(ByteStream stream) {
         for (int i = 0; i < pool.length; ++i) {
            ByteStream pooled = pool[i];
            if (pooled != null) {
               pool[i] = null;
               stream.moveTo(pooled);
               return pooled;
            }
         }
         throw new IllegalStateException();
      }

      public void release(ByteStream stream) {
         for (int i = 0; i < pool.length; ++i) {
            if (pool[i] == null) {
               pool[i] = stream;
               return;
            }
         }
         throw new IllegalStateException();
      }
   }

   public static class UnquotingProcessor extends Processor.BaseDelegating implements ResourceUtilizer, Session.ResourceKey<UnquotingProcessor.Context> {
      private static final ByteBuf NEWLINE = Unpooled.wrappedBuffer("\n".getBytes(StandardCharsets.UTF_8));
      private static final ByteBuf BACKSPACE = Unpooled.wrappedBuffer("\b".getBytes(StandardCharsets.UTF_8));
      private static final ByteBuf FORMFEED = Unpooled.wrappedBuffer("\f".getBytes(StandardCharsets.UTF_8));
      private static final ByteBuf CR = Unpooled.wrappedBuffer("\r".getBytes(StandardCharsets.UTF_8));
      private static final ByteBuf TAB = Unpooled.wrappedBuffer("\t".getBytes(StandardCharsets.UTF_8));

      public UnquotingProcessor(Processor delegate) {
         super(delegate);
      }

      @Override
      public void before(Session session) {
         super.before(session);
         Context context = session.getResource(this);
         context.reset();
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         Context context = session.getResource(this);
         int begin;
         if (context.unicode) {
            begin = offset + processUnicode(session, data, offset, length, isLastPart, context, 0);
         } else if (context.first && data.getByte(offset) == '"') {
            begin = offset + 1;
         } else {
            begin = offset;
         }

         for (int i = begin - offset; i < length; ++i) {
            if (context.escaped || data.getByte(offset + i) == '\\') {
               int fragmentLength = offset + i - begin;
               if (fragmentLength > 0) {
                  delegate.process(session, data, begin, fragmentLength, isLastPart && fragmentLength == length);
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

               switch (data.getByte(offset + i)) {
                  case 'n':
                     delegate.process(session, NEWLINE, 0, NEWLINE.readableBytes(), isLastPart && i == length - 1);
                     begin = offset + i + 1;
                     break;
                  case 'b':
                     delegate.process(session, BACKSPACE, 0, BACKSPACE.readableBytes(), isLastPart && i == length - 1);
                     begin = offset + i + 1;
                     break;
                  case 'f':
                     delegate.process(session, FORMFEED, 0, FORMFEED.readableBytes(), isLastPart && i == length - 1);
                     begin = offset + i + 1;
                     break;
                  case 'r':
                     delegate.process(session, CR, 0, CR.readableBytes(), isLastPart && i == length - 1);
                     begin = offset + i + 1;
                     break;
                  case 't':
                     delegate.process(session, TAB, 0, TAB.readableBytes(), isLastPart && i == length - 1);
                     begin = offset + i + 1;
                     break;
                  case 'u':
                     context.unicode = true;
                     ++i;
                     i = processUnicode(session, data, offset, length, isLastPart, context, i) - 1;
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
            } else if (isLastPart && i == length - 1 && data.getByte(offset + i) == '"') {
               --length;
            }
         }
         context.first = false;
         int fragmentLength = length - begin + offset;
         // we need to send this even if the length == 0 as when we have removed the last quote
         // the previous chunk had not isLastPart==true
         delegate.process(session, data, begin, fragmentLength, isLastPart);
         if (isLastPart) {
            context.reset();
         }
      }

      private int processUnicode(Session session, ByteBuf data, int offset, int length, boolean isLastPart, Context context, int i) {
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
            delegate.process(session, utf8, utf8.readerIndex(), utf8.readableBytes(), isLastPart && i == length - 1);
            context.unicode = false;
            context.unicodeChar = 0;
            context.unicodeDigits = 0;
         }
         return i;
      }

      @Override
      public void reserve(Session session) {
         super.reserve(session);
         session.declareResource(this, new Context());
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
}
