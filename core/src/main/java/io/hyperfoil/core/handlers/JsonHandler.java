package io.hyperfoil.core.handlers;

import java.util.function.Consumer;
import java.util.function.Function;

import org.kohsuke.MetaInfServices;

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
   public static class Builder extends BaseBuilder<Builder> implements RequestProcessorBuilder {
      @Override
      public JsonHandler build(boolean fragmented) {
         Processor processor = this.processor.build(fragmented);
         if (unquote) {
            processor = new UnquotingProcessor(processor);
         }
         return new JsonHandler(query, processor);
      }

      /**
       * Pass the selected parts to another processor.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<RequestProcessorBuilder> processor() {
         return new ServiceLoadedBuilderProvider<>(RequestProcessorBuilder.class, locator, this::processor);
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
