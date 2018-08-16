package io.sailrocket.core.extractors;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.DataExtractor;
import io.sailrocket.api.Session;
import io.sailrocket.core.machine.ResourceUtilizer;

public class JsonExtractor implements DataExtractor, ResourceUtilizer {
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
   public void extractData(ByteBuf data, Session session) {
      Context ctx = (Context) session.getObject(this);
      processor.before(session);
      ctx.parse(data, session);
      processor.after(session);
   }

   @Override
   public String toString() {
      return "JsonExtractor{" +
            "path='" + path + '\'' +
            ", recorder=" + processor +
            '}';
   }

   @Override
   public void reserve(io.sailrocket.core.machine.Session session) {
      session.setObject(this, new Context());
      if (processor instanceof ResourceUtilizer) {
         ((ResourceUtilizer) processor).reserve(session);
      }
   }

   private class Context {
      Selector.Context[] selectorContext = new Selector.Context[selectors.length];
      int level = -1, selectorLevel = 0;
      int selector = 0;
      boolean inQuote = false;
      boolean inAttrib = false;
      int attribStart = -1;
      int lastChar = -1;
      int fireStart = -1;

      public Context() {
         for (int i = 0; i < selectors.length; ++i) {
            selectorContext[i] = selectors[i].newContext();
         }
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
                     if (selectorLevel == level && attribStart >= 0 && selector < selectors.length && selectors[selector] instanceof AttribSelector) {
                        if (((AttribSelector) selectors[selector]).match(data, attribStart, lastChar)) {
                           onMatch(data);
                        }
                     }
                     attribStart = -1;
                     inAttrib = false;
                  }
                  break;
               case ',':
                  if (!inQuote) {
                     inAttrib = true;
                     attribStart = -1;
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
                  lastChar = data.readerIndex();
                  if (inAttrib && attribStart < 0) {
                     attribStart = data.readerIndex() - 1;
                  }
            }
         }
      }

      private void onMatch(ByteBuf data) {
         ++selector;
         if (selector < selectors.length) {
            ++selectorLevel;
         } else {
            fireStart = data.readerIndex();
         }
      }

      private void tryRecord(Session session, ByteBuf data) {
         if (selectorLevel == level && fireStart >= 0) {
            // fireStart is always before quotes here
            int start = fireStart;
            LOOP: while (start < data.writerIndex()) {
               switch (data.getByte(start)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     ++start;
                     break;
                  default:
                     break LOOP;
               }
            }
            int end = data.readerIndex() - 1;
            LOOP: while (end > start) {
               switch (data.getByte(end - 1)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     --end;
                     break;
                  default:
                     break LOOP;
               }
            }
            if (start == end) {
               // This happens when we try to select from a 0-length array
               // - as long as there are not quotes there's nothing to record.
               fireStart = -1;
               --selector;
               return;
            }
            if (data.getByte(start) == '"' && data.getByte(end - 1) == '"') {
               ++start;
               --end;
            }
            process(session, data, start, end);
         }
      }

      private void process(Session session, ByteBuf data, int start, int end) {
         processor.process(session, data, start, end - start);
         fireStart = -1;
         --selector;
      }

   }

   private interface Selector {
      Context newContext();

      interface Context {
      }
   }

   private class AttribSelector implements Selector {
      byte[] name;

      public AttribSelector(byte[] name) {
         this.name = name;
      }

      public boolean match(ByteBuf data, int start, int end) {
         for (int i = 0; i < name.length && i < end - start; ++i) {
            if (name[i] != data.getByte(start + i)) return false;
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
      boolean active = false;
      int currentItem = 0;
   }
}
