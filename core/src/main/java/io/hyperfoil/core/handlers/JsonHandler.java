package io.hyperfoil.core.handlers;

import java.util.function.Consumer;
import java.util.function.Function;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;

public class JsonHandler extends JsonParser implements Processor, Session.ResourceKey<JsonHandler.Context> {

   public JsonHandler(String query, Processor processor) {
      super(query.trim(), processor);

   }

   @Override
   public void before(Session session) {
      processor.before(session);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLast) {
      Context ctx = session.getResource(this);
      ctx.parse(ctx.wrap(data, offset, length), session);
   }

   @Override
   public void after(Session session) {
      processor.after(session);
      Context ctx = session.getResource(this);
      ctx.reset();
   }

   @Override
   public String toString() {
      return "JsonHandler{" +
            "query='" + query + '\'' +
            ", recorder=" + processor +
            '}';
   }

   @Override
   public void reserve(Session session) {
      super.reserve(session);
      session.declareResource(this, new Context());
   }

   @Override
   protected void fireMatch(JsonParser.Context context, Session session, ByteStream data, int offset, int length, boolean isLastPart) {
      processor.process(session, ((ByteBufByteStream) data).buffer, offset, length, isLastPart);
   }

   public class Context extends JsonParser.Context {
      ByteBufByteStream actualStream;

      Context() {
         super(self -> new ByteBufByteStream(null, self::release));
         actualStream = new ByteBufByteStream(this::retain, null);
      }

      public ByteStream wrap(ByteBuf data, int offset, int length) {
         actualStream.buffer = data;
         actualStream.readerIndex = offset;
         return actualStream;
      }
   }

   /**
    * Parses JSON responses using simple queries.
    */
   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("json")
   public static class Builder implements RequestProcessorBuilder, InitFromParam<Builder> {
      private Locator locator;
      private String query;
      private boolean unquote = true;
      private RequestProcessorBuilder processor;

      @Override
      public Builder setLocator(Locator locator) {
         this.locator = locator;
         return this;
      }

      /**
       * @param param Either <code>query -&gt; variable</code> or <code>variable &lt;- query</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
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
            throw new BenchmarkDefinitionException("Cannot parse json handler specification: '" + param + "', use 'query -> var' or 'var <- query'");
         }
         return query(query.trim())
               .processor(new SimpleRecorder.Builder().init(var.trim()));
      }

      @Override
      public JsonHandler build(boolean fragmented) {
         Processor processor = this.processor.build(fragmented);
         if (unquote) {
            processor = new UnquotingProcessor(processor);
         }
         return new JsonHandler(query, processor);
      }

      /**
       * Query selecting the part of JSON.
       *
       * @param query Query.
       * @return Self.
       */
      public Builder query(String query) {
         this.query = query;
         return this;
      }

      /**
       * Shortcut to store selected parts in an array in the session. Must follow the pattern <code>variable[maxSize]</code>
       *
       * @param varAndSize Array name.
       * @return Self.
       */
      public Builder toArray(String varAndSize) {
         return processor(new ArrayRecorder.Builder().init(varAndSize));
      }

      /**
       * Automatically unquote and unescape the input values. By default true.
       *
       * @param unquote Do unquote and unescape?
       * @return Builder.
       */
      public Builder unquote(boolean unquote) {
         this.unquote = unquote;
         return this;
      }

      public Builder processor(RequestProcessorBuilder processor) {
         this.processor = processor;
         return this;
      }

      /**
       * Pass the selected parts to another processor.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<RequestProcessorBuilder> processor() {
         return new ServiceLoadedBuilderProvider<>(RequestProcessorBuilder.class, locator, this::processor);
      }

      @Override
      public JsonHandler.Builder copy(Locator locator) {
         return new Builder().setLocator(locator).query(query).processor(processor);
      }
   }

   static class ByteBufByteStream implements ByteStream {
      private final Function<ByteStream, ByteStream> retain;
      private final Consumer<ByteStream> release;
      private ByteBuf buffer;
      private int readerIndex;

      ByteBufByteStream(Function<ByteStream, ByteStream> retain, Consumer<ByteStream> release) {
         this.retain = retain;
         this.release = release;
      }

      @Override
      public boolean isReadable() {
         return readerIndex < buffer.writerIndex();
      }

      @Override
      public byte readByte() {
         assert isReadable();
         return buffer.getByte(readerIndex++);
      }

      @Override
      public int getByte(int index) {
         return buffer.getByte(index);
      }

      @Override
      public int writerIndex() {
         return buffer.writerIndex();
      }

      @Override
      public int readerIndex() {
         return readerIndex;
      }

      @Override
      public void release() {
         buffer.release();
         buffer = null;
         readerIndex = -1;
         release.accept(this);
      }

      @Override
      public ByteStream retain() {
         buffer.retain();
         return retain.apply(this);
      }

      @Override
      public void moveTo(ByteStream other) {
         ByteBufByteStream o = (ByteBufByteStream) other;
         assert o.buffer == null;
         o.buffer = buffer;
         o.readerIndex = readerIndex;
         buffer = null;
         readerIndex = -1;
      }
   }
}
