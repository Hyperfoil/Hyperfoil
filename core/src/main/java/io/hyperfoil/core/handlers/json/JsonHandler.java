package io.hyperfoil.core.handlers.json;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;

public class JsonHandler extends JsonParser implements Processor, ResourceUtilizer, Session.ResourceKey<JsonHandler.Context> {

   public JsonHandler(String query, boolean delete, Transformer replace, Processor processor) {
      super(query.trim(), delete, replace, processor);

   }

   @Override
   public void before(Session session) {
      processor.before(session);
      if (replace != null) {
         replace.before(session);
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLast) {
      if (data.readableBytes() == 0) {
         return;
      }
      Context ctx = session.getResource(this);
      ctx.parse(ctx.wrap(data, offset, length), session, isLast);
   }

   @Override
   public void after(Session session) {
      if (replace != null) {
         replace.after(session);
      }
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
      session.declareResource(this, Context::new);
   }

   @Override
   protected void record(JsonParser.Context context, Session session, ByteStream data, int offset, int length,
         boolean isLastPart) {
      processor.process(session, ((ByteBufByteStream) data).buffer(), offset, length, isLastPart);
   }

   public class Context extends JsonParser.Context {
      ByteBufByteStream actualStream;

      Context() {
         super(self -> new ByteBufByteStream(null, self::release));
         actualStream = new ByteBufByteStream(this::retain, null);
      }

      public ByteStream wrap(ByteBuf data, int offset, int length) {
         return actualStream.wrap(data, offset, offset + length);
      }

      @Override
      protected void replaceConsumer(Void ignored, Session session, ByteStream data, int offset, int length,
            boolean lastFragment) {
         replace.transform(session, ((ByteBufByteStream) data).buffer(), offset, length, lastFragment, replaceBuffer);
      }
   }

   /**
    * Parses JSON responses using simple queries.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("json")
   public static class Builder extends BaseBuilder<Builder> implements Processor.Builder {
      @Override
      public JsonHandler build(boolean fragmented) {
         Processor processor = processors.build(fragmented || unquote);
         Transformer replace = this.replace == null ? null : this.replace.build(fragmented);
         if (unquote) {
            processor = new JsonUnquotingTransformer(processor);
            if (replace != null) {
               replace = new JsonUnquotingTransformer(replace);
            }
         }
         return new JsonHandler(query, delete, replace, processor);
      }

      /**
       * If neither `delete` or `replace` was set this processor will be called with the selected parts.
       * In the other case the processor will be called with chunks of full (modified) JSON.
       * <p>
       * Note that the `processor.before()` and `processor.after()` methods are called only once for each request,
       * not for the individual filtered items.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Processor.Builder> processor() {
         return new ServiceLoadedBuilderProvider<>(Processor.Builder.class, processors::processor);
      }
   }
}
