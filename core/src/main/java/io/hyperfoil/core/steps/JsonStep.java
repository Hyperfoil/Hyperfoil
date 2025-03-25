package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.handlers.json.ByteArrayByteStream;
import io.hyperfoil.core.handlers.json.ByteStream;
import io.hyperfoil.core.handlers.json.JsonParser;
import io.hyperfoil.core.handlers.json.JsonUnquotingTransformer;
import io.hyperfoil.core.session.SessionFactory;

public class JsonStep implements Step {
   private final ByteArrayParser byteArrayParser;
   private final ReadAccess fromVar;

   private JsonStep(ReadAccess fromVar, String query, boolean delete, Transformer replace, Processor processor) {
      this.fromVar = fromVar;
      this.byteArrayParser = new ByteArrayParser(query, delete, replace, processor);
   }

   @Override
   public boolean invoke(Session session) {
      Object object = fromVar.getObject(session);
      if (object instanceof byte[] array) {
         if (array.length == 0) {
            return true;
         }
         ByteArrayParser.Context ctx = session.getResource(byteArrayParser);
         byteArrayParser.before(session);
         ctx.parse(ctx.wrap((byte[]) object), session, true);
         byteArrayParser.after(session);
         ctx.reset();
      } else {
         throw new IllegalStateException("Unexpected format of input (expecting byte[]): " + object);
      }
      return true;
   }

   /**
    * Parse JSON in variable into another variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("json")
   public static class Builder extends JsonParser.BaseBuilder<Builder> implements StepBuilder<Builder> {
      private BaseSequenceBuilder<?> parent;
      private String fromVar;

      /**
       * Variable to load JSON from.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      @Override
      public List<Step> build() {
         validate();
         if (fromVar == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'fromVar'");
         }
         Processor processor = processors.build(unquote);
         Transformer replace = this.replace == null ? null : this.replace.build(unquote);
         if (unquote) {
            processor = processor == null ? null : new JsonUnquotingTransformer(processor);
            replace = replace == null ? null : new JsonUnquotingTransformer(replace);
         }
         return Collections.singletonList(new JsonStep(SessionFactory.readAccess(fromVar), query, delete, replace, processor));
      }

      public Builder addTo(BaseSequenceBuilder<?> parent) {
         if (this.parent != null) {
            throw new UnsupportedOperationException("Cannot add builder " + getClass().getName() + " to another sequence!");
         }
         parent.stepBuilder(this);
         this.parent = Objects.requireNonNull(parent);
         return self();
      }
   }

   private static class ByteArrayParser extends JsonParser
         implements ResourceUtilizer, Session.ResourceKey<ByteArrayParser.Context> {
      ByteArrayParser(String query, boolean delete, Transformer replace, Processor processor) {
         super(query, delete, replace, processor);
      }

      @Override
      public void reserve(Session session) {
         session.declareResource(this, Context::new);
      }

      @Override
      protected void record(JsonParser.Context context, Session session, ByteStream data, int offset, int length,
            boolean isLastPart) {
         Context ctx = (Context) context;
         byte[] array = ((ByteArrayByteStream) data).array();
         processor.process(session, ctx.buffer.wrap(array), offset, length, isLastPart);
      }

      public void before(Session session) {
         processor.before(session);
         if (replace != null) {
            replace.before(session);
         }
      }

      public void after(Session session) {
         if (replace != null) {
            replace.after(session);
         }
         processor.after(session);
      }

      public class Context extends JsonParser.Context {
         ReadonlyWrappingByteBuf buffer = new ReadonlyWrappingByteBuf();
         ByteArrayByteStream actualStream = new ByteArrayByteStream(this::retain, null);
         boolean set;

         protected Context() {
            super(self -> new ByteArrayByteStream(null, self::release));
         }

         @Override
         public void reset() {
            super.reset();
            set = false;
         }

         @Override
         protected void replaceConsumer(Void ignored, Session session, ByteStream data, int offset, int length,
               boolean lastFragment) {
            replace.transform(session, buffer.wrap(((ByteArrayByteStream) data).array()), offset, length, lastFragment,
                  replaceBuffer);
         }

         public ByteStream wrap(byte[] object) {
            return actualStream.wrap(object);
         }
      }
   }
}
