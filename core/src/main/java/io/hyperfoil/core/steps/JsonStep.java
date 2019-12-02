package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.handlers.ByteStream;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.handlers.JsonParser;
import io.hyperfoil.core.session.SessionFactory;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class JsonStep extends BaseStep implements ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(JsonStep.class);

   private final ByteArrayParser byteArrayParser;
   private final Access fromVar;
   private final Access toVar;
   private final DataFormat format;

   private JsonStep(String fromVar, String query, String toVar, DataFormat format) {
      this.fromVar = SessionFactory.access(fromVar);
      this.byteArrayParser = new ByteArrayParser(query);
      this.toVar = SessionFactory.access(toVar);
      this.format = format;
   }

   @Override
   public boolean invoke(Session session) {
      Object object = fromVar.getObject(session);
      if (object instanceof byte[]) {
         ByteArrayParser.Context ctx = session.getResource(byteArrayParser);
         ctx.parse(ctx.wrap((byte[]) object), session);
         ctx.reset();
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(byteArrayParser, byteArrayParser.newContext());
      toVar.declareObject(session);
   }

   /**
    * Parse JSON in variable into another variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("json")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String fromVar;
      private String query;
      private String toVar;
      private DataFormat format = DataFormat.STRING;

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

      /**
       * JSON query to apply.
       *
       * @param query Query.
       * @return Self.
       */
      public Builder query(String query) {
         this.query = query;
         return this;
      }

      /**
       * Variable to store the result. If there are multiple matches, the variable will be overwritten and last match wins.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Conversion to apply on the matching parts.
       *
       * @param format Data format.
       * @return Self.
       */
      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      @Override
      public List<Step> build() {
         if (fromVar == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'fromVar'");
         }
         if (query == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'query'");
         }
         if (toVar == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'toVar'");
         }
         return Collections.singletonList(new JsonStep(fromVar, query, toVar, format));
      }
   }

   private class ByteArrayParser extends JsonParser<Session> implements Session.ResourceKey<ByteArrayParser.Context> {
      public ByteArrayParser(String query) {
         super(query);
      }

      public Context newContext() {
         return new Context();
      }

      @Override
      protected void fireMatch(JsonParser<Session>.Context context, Session session, ByteStream data, int offset, int length, boolean isLastPart) {
         if (!isLastPart) {
            throw new IllegalStateException("jsonQuery step expecting defragmented data");
         }
         Context ctx = (Context) context;
         if (!ctx.set) {
            toVar.setObject(session, format.convert(((ByteArrayByteStream) data).array, offset, length));
            ctx.set = true;
         } else {
            log.warn("Second match, dropping data!");
         }
      }

      public class Context extends JsonParser<Session>.Context {
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

         public ByteStream wrap(byte[] object) {
            actualStream.set(object);
            return actualStream;
         }
      }
   }

   static class ByteArrayByteStream implements ByteStream {
      final Function<ByteStream, ByteStream> retain;
      final Consumer<ByteStream> release;
      byte[] array;
      int readerIndex;

      ByteArrayByteStream(Function<ByteStream, ByteStream> retain, Consumer<ByteStream> release) {
         this.retain = retain;
         this.release = release;
      }

      @Override
      public boolean isReadable() {
         return readerIndex < array.length;
      }

      @Override
      public byte readByte() {
         return array[readerIndex++];
      }

      @Override
      public int getByte(int index) {
         return array[index];
      }

      @Override
      public int writerIndex() {
         return array.length;
      }

      @Override
      public int readerIndex() {
         return readerIndex;
      }

      @Override
      public void release() {
         release.accept(this);
      }

      @Override
      public ByteStream retain() {
         return retain.apply(this);
      }

      @Override
      public void moveTo(ByteStream other) {
         ByteArrayByteStream o = (ByteArrayByteStream) other;
         o.array = array;
         o.readerIndex = readerIndex;
         array = null;
      }

      public void set(byte[] array) {
         this.array = array;
         this.readerIndex = 0;
      }
   }
}
