package io.hyperfoil.core.handlers;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.Session;

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
      for (;;) {
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
      int attribStartPart;
      int attribStartIndex;
      int lastCharPart;
      int lastCharIndex;
      int valueStartPart;
      int valueStartIndex;
      ByteStream[] parts = new ByteStream[MAX_PARTS];
      int nextPart = 0;
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
         attribStartPart = -1;
         attribStartIndex = -1;
         lastCharPart = -1;
         lastCharIndex = -1;
         valueStartPart = -1;
         valueStartIndex = -1;
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
            switch (data.readByte()) {
               case ' ':
               case '\n':
               case '\t':
               case '\r':
                  // ignore whitespace
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
                  inQuote = !inQuote;
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
         }
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
            LOOP: while (valueStartIndex < buf.writerIndex()) {
               switch (buf.getByte(valueStartIndex)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     ++valueStartIndex;
                     if (valueStartIndex >= buf.writerIndex() && valueStartPart >= 0) {
                        valueStartPart++;
                        if (valueStartPart >= parts.length || (buf = parts[valueStartPart]) == null) {
                           buf = data;
                           valueStartPart = -1;
                        }
                     }
                     break;
                  default:
                     break LOOP;
               }
            }
            int end = data.readerIndex() - 1;
            int endPart = nextPart;
            buf = data;
            LOOP: while (end > valueStartIndex || valueStartPart >= 0 && endPart > valueStartPart) {
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
            // unquote
            ByteStream startBuf = buf(data, valueStartPart);
            ByteStream endBuf = buf(data, endPart);
            if (startBuf.getByte(valueStartIndex) == '"' && endBuf.getByte(end - 1) == '"') {
               ++valueStartIndex;
               if (valueStartPart >= 0 && valueStartIndex > parts[valueStartPart].writerIndex()) {
                  incrementValueStartPart();
               }
               --end;
               if (end == 0) {
                  endPart--;
                  endBuf = parts[endPart];
                  end = endBuf.writerIndex();
               }
            }
            while (valueStartPart >= 0 && valueStartPart != endPart) {
               int valueEndIndex = endPart == valueStartPart ? end : parts[valueStartPart].writerIndex();
               fireMatch(this, source, parts[valueStartPart], valueStartIndex, valueEndIndex - valueStartIndex, false);
               incrementValueStartPart();
            }
            fireMatch(this, source, endBuf, valueStartIndex, end - valueStartIndex, true);
            valueStartIndex = -1;
            --selector;
         }
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
}
