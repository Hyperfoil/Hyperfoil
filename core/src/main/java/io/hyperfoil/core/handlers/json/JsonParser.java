package io.hyperfoil.core.handlers.json;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.handlers.MultiProcessor;
import io.hyperfoil.core.handlers.StoreShortcuts;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

public abstract class JsonParser implements Serializable {
   protected static final Logger log = LogManager.getLogger(JsonParser.class);
   protected static final int INITIAL_PARTS = 16;

   protected final String query;
   protected final boolean delete;
   protected final Transformer replace;
   protected final Processor processor;
   @Visitor.Ignore
   private final JsonParser.Selector[] selectors;
   @Visitor.Ignore
   private final StreamQueue.Consumer<Context, Session> record = JsonParser.this::record;

   public JsonParser(String query, boolean delete, Transformer replace, Processor processor) {
      this.query = query;
      this.delete = delete;
      this.replace = replace;
      this.processor = processor;

      byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);
      if (queryBytes.length == 0 || queryBytes[0] != '.') {
         throw new BenchmarkDefinitionException("Path should start with '.'");
      }
      ArrayList<Selector> selectors = new ArrayList<>();
      int next = 1;
      for (int i = 1; i < queryBytes.length; ++i) {
         if (queryBytes[i] == '[' || queryBytes[i] == '.' && next < i) {
            while (queryBytes[next] == '.')
               ++next;
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
         while (queryBytes[next] == '.')
            ++next;
         selectors.add(new AttribSelector(Arrays.copyOfRange(queryBytes, next, queryBytes.length)));
      }
      this.selectors = selectors.toArray(new JsonParser.Selector[0]);
   }

   protected abstract void record(Context context, Session session, ByteStream data, int offset, int length,
         boolean isLastPart);

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

      boolean match(StreamQueue stream, int start, int end) {
         assert start <= end;
         // TODO: move this to StreamQueue and optimize access
         for (int i = 0; i < name.length && i < end - start; ++i) {
            if (name[i] != stream.getByte(start + i))
               return false;
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

   protected abstract class Context implements Session.Resource {
      Selector.Context[] selectorContext = new Selector.Context[selectors.length];
      int level;
      int selectorLevel;
      int selector;
      boolean inQuote;
      boolean inKey;
      boolean escaped;
      StreamQueue stream = new StreamQueue(INITIAL_PARTS);
      int keyStartIndex;
      int lastCharIndex; // end of key name
      int valueStartIndex;
      int lastOutputIndex; // last byte we have written out
      int safeOutputIndex; // last byte we could definitely write out
      ArrayDeque<ByteStream> pool = new ArrayDeque<>(INITIAL_PARTS);
      protected final ByteBuf replaceBuffer = PooledByteBufAllocator.DEFAULT.buffer();
      final StreamQueue.Consumer<Void, Session> replaceConsumer = this::replaceConsumer;
      final Function<Context, ByteStream> byteStreamFactory;

      protected Context(Function<Context, ByteStream> byteStreamSupplier) {
         this.byteStreamFactory = byteStreamSupplier;
         for (int i = 0; i < selectors.length; ++i) {
            selectorContext[i] = selectors[i].newContext();
         }
         reset();
      }

      public void reset() {
         for (Selector.Context ctx : selectorContext) {
            if (ctx != null)
               ctx.reset();
         }
         level = -1;
         selectorLevel = 0;
         selector = 0;
         inQuote = false;
         inKey = false;
         escaped = false;
         keyStartIndex = -1;
         lastCharIndex = -1;
         valueStartIndex = -1;
         lastOutputIndex = 0;
         safeOutputIndex = 0;
         stream.reset();
         replaceBuffer.clear();
      }

      @Override
      public void destroy() {
         replaceBuffer.release();
         stream.reset();
      }

      private Selector.Context current() {
         return selectorContext[selector];
      }

      public void parse(ByteStream data, Session session, boolean isLast) {
         final int readableBytes = data.writerIndex() - data.readerIndex();
         int readerIndex = stream.append(data);
         for (int i = 0; i < readableBytes; i++) {
            int b = stream.getByte(readerIndex++);
            switch (b) {
               case -1:
                  throw new IllegalStateException("End of input while the JSON is not complete.");
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
                     inKey = true;
                     if (valueStartIndex < 0) {
                        safeOutputIndex = readerIndex;
                     }
                     // TODO assert we have active attrib selector
                  }
                  break;
               case '}':
                  if (!inQuote) {
                     tryRecord(session, readerIndex);
                     if (level == selectorLevel) {
                        --selectorLevel;
                        --selector;
                     }
                     if (valueStartIndex < 0) {
                        safeOutputIndex = readerIndex;
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
                     if (selectorLevel == level && keyStartIndex >= 0 && selector < selectors.length
                           && selectors[selector] instanceof AttribSelector) {
                        AttribSelector selector = (AttribSelector) selectors[this.selector];
                        if (selector.match(stream, keyStartIndex, lastCharIndex)) {
                           if (onMatch(readerIndex) && (delete || replace != null)) {
                              // omit key's starting quote
                              int outputEnd = keyStartIndex - 1;
                              // remove possible comma before the key
                              LOOP: while (true) {
                                 switch (stream.getByte(outputEnd - 1)) {
                                    case ' ':
                                    case '\n':
                                    case '\t':
                                    case '\r':
                                    case ',':
                                       --outputEnd;
                                       break;
                                    default:
                                       break LOOP;
                                 }
                              }
                              stream.consume(lastOutputIndex, outputEnd, record, this, session, false);
                              lastOutputIndex = outputEnd;
                           }
                        }
                     }
                     keyStartIndex = -1;
                     if (valueStartIndex < 0) {
                        safeOutputIndex = readerIndex;
                     }
                     inKey = false;
                  }
                  break;
               case ',':
                  if (!inQuote) {
                     inKey = true;
                     keyStartIndex = -1;
                     tryRecord(session, readerIndex);
                     if (selectorLevel == level && selector < selectors.length && current() instanceof ArraySelectorContext) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        if (asc.active) {
                           asc.currentItem++;
                        }
                        if (((ArraySelector) selectors[selector]).matches(asc)) {
                           if (onMatch(readerIndex) && (delete || replace != null)) {
                              // omit the ','
                              stream.consume(lastOutputIndex, readerIndex - 1, record, this, session, false);
                              lastOutputIndex = readerIndex - 1;
                           }
                        }
                     }
                  }
                  break;
               case '[':
                  if (!inQuote) {
                     if (valueStartIndex < 0) {
                        safeOutputIndex = readerIndex;
                     }
                     ++level;
                     if (selectorLevel == level && selector < selectors.length
                           && selectors[selector] instanceof ArraySelector) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        asc.active = true;
                        if (((ArraySelector) selectors[selector]).matches(asc)) {
                           if (onMatch(readerIndex) && (delete || replace != null)) {
                              stream.consume(lastOutputIndex, readerIndex, record, this, session, false);
                              lastOutputIndex = readerIndex;
                           }
                        }
                     }
                  }
                  break;
               case ']':
                  if (!inQuote) {
                     tryRecord(session, readerIndex);
                     if (selectorLevel == level && selector < selectors.length && current() instanceof ArraySelectorContext) {
                        ArraySelectorContext asc = (ArraySelectorContext) current();
                        asc.active = false;
                        --selectorLevel;
                     }
                     if (valueStartIndex < 0) {
                        safeOutputIndex = readerIndex;
                     }
                     keyStartIndex = -1;
                     inKey = false;
                     --level;
                  }
                  break;
               default:
                  lastCharIndex = readerIndex;
                  if (inKey && keyStartIndex < 0) {
                     keyStartIndex = readerIndex - 1;
                  }
            }
            if (b != '\\') {
               escaped = false;
            }
         }
         if (keyStartIndex >= 0 || valueStartIndex >= 0) {
            stream.releaseUntil(Math.min(Math.min(keyStartIndex, valueStartIndex), safeOutputIndex));
            if (isLast) {
               throw new IllegalStateException("End of input while the JSON is not complete.");
            }
         } else {
            if ((delete || replace != null) && lastOutputIndex < safeOutputIndex) {
               stream.consume(lastOutputIndex, safeOutputIndex, record, this, session, isLast);
               lastOutputIndex = safeOutputIndex;
            }
            stream.releaseUntil(readerIndex);
         }
      }

      private boolean onMatch(int readerIndex) {
         ++selector;
         if (selector < selectors.length) {
            ++selectorLevel;
            return false;
         } else {
            valueStartIndex = readerIndex;
            return true;
         }
      }

      private void tryRecord(Session session, int readerIndex) {
         if (selectorLevel == level && valueStartIndex >= 0) {
            // valueStartIndex is always before quotes here
            LOOP: while (true) {
               switch (stream.getByte(valueStartIndex)) {
                  case ' ':
                  case '\n':
                  case '\r':
                  case '\t':
                     ++valueStartIndex;
                     break;
                  case -1:
                  default:
                     break LOOP;
               }
            }
            int end = readerIndex - 1;
            LOOP: while (end > valueStartIndex) {
               switch (stream.getByte(end - 1)) {
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
            if (valueStartIndex == end) {
               // This happens when we try to select from a 0-length array
               // - as long as there are not quotes there's nothing to record.
               valueStartIndex = -1;
               --selector;
               return;
            }
            if (replace != null) {
               // The buffer cannot be overwritten as if the processor is caching input
               // (this happens when we're defragmenting) we would overwrite the underlying data
               replaceBuffer.readerIndex(replaceBuffer.writerIndex());
               stream.consume(valueStartIndex, end, replaceConsumer, null, session, true);
               // If the result is empty, don't write the key
               if (replaceBuffer.isReadable()) {
                  stream.consume(lastOutputIndex, valueStartIndex, record, this, session, false);
                  processor.process(session, replaceBuffer, replaceBuffer.readerIndex(), replaceBuffer.readableBytes(), false);
               }
            } else if (!delete) {
               stream.consume(valueStartIndex, end, record, this, session, true);
            }
            lastOutputIndex = end;
            valueStartIndex = -1;
            --selector;
         }
      }

      public ByteStream retain(ByteStream stream) {
         ByteStream pooled = pool.poll();
         if (pooled == null) {
            pooled = byteStreamFactory.apply(this);
         }
         stream.moveTo(pooled);
         return pooled;
      }

      public void release(ByteStream stream) {
         pool.add(stream);
      }

      protected abstract void replaceConsumer(Void ignored, Session session, ByteStream data, int offset, int length,
            boolean lastFragment);
   }

   public abstract static class BaseBuilder<S extends BaseBuilder<S>> implements InitFromParam<S>, StoreShortcuts.Host {
      protected String query;
      protected boolean unquote = true;
      protected boolean delete;
      protected Transformer.Builder replace;
      protected MultiProcessor.Builder<S, ?> processors = new MultiProcessor.Builder<>(self());
      protected StoreShortcuts<S> storeShortcuts = new StoreShortcuts<>(self());

      public void accept(Processor.Builder storeProcessor) {
         processors.processor(storeProcessor);
      }

      /**
       * @param param Either <code>query -&gt; variable</code> or <code>variable &lt;- query</code>.
       * @return Self.
       */
      @Override
      public S init(String param) {
         String query;
         String var;
         if (param.contains("->")) {
            String[] parts = param.split("->");
            query = parts[0];
            var = parts[1];
         } else if (param.contains("<-")) {
            String[] parts = param.split("->");
            query = parts[1];
            var = parts[0];
         } else {
            throw new BenchmarkDefinitionException(
                  "Cannot parse json query specification: '" + param + "', use 'query -> var' or 'var <- query'");
         }
         storeShortcuts.toVar(var.trim());
         return query(query.trim());
      }

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      /**
       * Query selecting the part of JSON.
       *
       * @param query Query.
       * @return Self.
       */
      public S query(String query) {
         this.query = query;
         return self();
      }

      /**
       * Automatically unquote and unescape the input values. By default true.
       *
       * @param unquote Do unquote and unescape?
       * @return Builder.
       */
      public S unquote(boolean unquote) {
         this.unquote = unquote;
         return self();
      }

      /**
       * If this is set to true, the selected key will be deleted from the JSON and the modified JSON will be passed
       * to the <code>processor</code>.
       *
       * @param delete Should the selected query be deleted?
       * @return Self.
       */
      public S delete(boolean delete) {
         this.delete = delete;
         return self();
      }

      /**
       * Custom transformation executed on the value of the selected item.
       * Note that the output value must contain quotes (if applicable) and be correctly escaped.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Transformer.Builder> replace() {
         return new ServiceLoadedBuilderProvider<>(Transformer.Builder.class, this::replace);
      }

      public S replace(Transformer.Builder replace) {
         if (this.replace != null) {
            throw new BenchmarkDefinitionException("Calling replace twice!");
         }
         this.replace = replace;
         return self();
      }

      /**
       * Replace value of selected item with value generated through a
       * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a>.
       * Note that the result must contain quotes and be correctly escaped.
       *
       * @param pattern Pattern format.
       * @return Self.
       */
      public S replace(String pattern) {
         return replace(fragmented -> new Pattern(pattern, false)).unquote(false);
      }

      @Embed
      public MultiProcessor.Builder<S, ?> processors() {
         return processors;
      }

      @Embed
      public StoreShortcuts<S> storeShortcuts() {
         return storeShortcuts;
      }

      protected void validate() {
         if (query == null) {
            throw new BenchmarkDefinitionException("Missing 'query'");
         } else if (processors.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing processor - use 'processor', 'toVar' or 'toArray'");
         }
      }
   }
}
