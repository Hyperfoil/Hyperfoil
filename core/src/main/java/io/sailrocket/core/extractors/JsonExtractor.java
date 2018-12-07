package io.sailrocket.core.extractors;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.http.BodyExtractor;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;

public class JsonExtractor implements BodyExtractor, ResourceUtilizer, Session.ResourceKey<JsonExtractor.Context> {
   private static final int MAX_PARTS = 16;

   private final String path;
   private final Session.Processor processor;
   private final Selector[] selectors;

   public JsonExtractor(String path, Session.Processor processor) {
      this.path = path.trim();
      this.processor = processor;
      try {
         byte[] pathBytes = this.path.getBytes("UTF-8");
         if (pathBytes.length == 0 || pathBytes[0] != '.') {
            throw new IllegalArgumentException("Path should start with '.'");
         }
         ArrayList<Selector> selectors = new ArrayList<>();
         int next = 1;
         for (int i = 1; i < pathBytes.length; ++i) {
            if (pathBytes[i] == '[' || pathBytes[i] == '.' && next < i) {
               while (pathBytes[next] == '.') ++next;
               selectors.add(new AttribSelector(Arrays.copyOfRange(pathBytes, next, i)));
               next = i + 1;
            }
            if (pathBytes[i] == '[') {
               ArraySelector arraySelector = new ArraySelector();
               ++i;
               int startIndex = i, endIndex = i;
               for (; i < pathBytes.length; ++i) {
                  if (pathBytes[i] == ']') {
                     if (endIndex < i) {
                        arraySelector.rangeEnd = bytesToInt(pathBytes, startIndex, i);
                        if (startIndex == endIndex) {
                           arraySelector.rangeStart = arraySelector.rangeEnd;
                        }
                     }
                     selectors.add(arraySelector);
                     next = i + 1;
                     break;
                  } else if (pathBytes[i] == ':') {
                     if (startIndex < i) {
                        arraySelector.rangeStart = bytesToInt(pathBytes, startIndex, i);
                     }
                     endIndex = i + 1;
                  }
               }
            }
         }
         if (next < pathBytes.length) {
            while (pathBytes[next] == '.') ++next;
            selectors.add(new AttribSelector(Arrays.copyOfRange(pathBytes, next, pathBytes.length)));
         }
         this.selectors = selectors.toArray(new Selector[0]);
      } catch (UnsupportedEncodingException e) {
         throw new IllegalStateException(e);
      }
   }

   private static int bytesToInt(byte[] bytes, int start, int end) {
      int value = 0;
      for (;;) {
         if (bytes[start] < '0' || bytes[start] > '9') {
            throw new IllegalArgumentException("Invalid range specification: " + new String(bytes));
         }
         value += bytes[start] - '0';
         if (++start >= end) {
            return value;
         } else {
            value *= 10;
         }
      }
   }

   @Override
   public void beforeData(Request request) {
      processor.before(request.session);
      Context ctx = request.session.getResource(this);
      ctx.reset();
   }

   @Override
   public void extractData(Request request, ByteBuf data) {
      Context ctx = request.session.getResource(this);
      ctx.parse(data, request.session);
   }

   @Override
   public void afterData(Request request) {
      processor.after(request.session);

      Context ctx = request.session.getResource(this);
      for (int i = 0; i < ctx.parts.length; ++i) {
         if (ctx.parts[i] == null) break;
         ctx.parts[i].release();
         ctx.parts[i] = null;
      }
   }

   @Override
   public String toString() {
      return "JsonExtractor{" +
            "path='" + path + '\'' +
            ", recorder=" + processor +
            '}';
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new Context());
      if (processor instanceof ResourceUtilizer) {
         ((ResourceUtilizer) processor).reserve(session);
      }
   }

   class Context implements Session.Resource {
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
      ByteBuf[] parts = new ByteBuf[MAX_PARTS];
      int nextPart = 0;

      public Context() {
         for (int i = 0; i < selectors.length; ++i) {
            selectorContext[i] = selectors[i].newContext();
         }
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
      }

      private Selector.Context current() {
         return selectorContext[selector];
      }

      private void parse(ByteBuf data, Session session) {
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
                     tryRecord(session, data);
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
                     tryRecord(session, data);
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
                     tryRecord(session, data);
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

      private void onMatch(ByteBuf data) {
         ++selector;
         if (selector < selectors.length) {
            ++selectorLevel;
         } else {
            valueStartPart = -1;
            valueStartIndex = data.readerIndex();
         }
      }

      private void tryRecord(Session session, ByteBuf data) {
         if (selectorLevel == level && valueStartIndex >= 0) {
            // valueStartIndex is always before quotes here
            ByteBuf buf = valueStartPart < 0 ? data : parts[valueStartPart];
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
            ByteBuf startBuf = buf(data, valueStartPart);
            ByteBuf endBuf = buf(data, endPart);
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
               processor.process(session, parts[valueStartPart], valueStartIndex, valueEndIndex - valueStartIndex, false);
               incrementValueStartPart();
            }
            processor.process(session, endBuf, valueStartIndex, end - valueStartIndex, true);
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

      private ByteBuf buf(ByteBuf data, int part) {
         if (part < 0 || part >= parts.length || parts[part] == null) {
            return data;
         }
         return parts[part];
      }
   }

   private interface Selector {
      Context newContext();

      interface Context {
         void reset();
      }
   }

   private class AttribSelector implements Selector {
      byte[] name;

      public AttribSelector(byte[] name) {
         this.name = name;
      }

      public boolean match(ByteBuf data, int start, int end, int offset) {
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

   private class ArraySelector implements Selector {
      int rangeStart = 0;
      int rangeEnd = Integer.MAX_VALUE;

      @Override
      public Context newContext() {
         return new ArraySelectorContext();
      }

      public boolean matches(ArraySelectorContext context) {
         return context.active && context.currentItem >= rangeStart && context.currentItem <= rangeEnd;
      }
   }

   private class ArraySelectorContext implements Selector.Context {
      boolean active;
      int currentItem;

      @Override
      public void reset() {
         active = false;
         currentItem = 0;
      }
   }
}
