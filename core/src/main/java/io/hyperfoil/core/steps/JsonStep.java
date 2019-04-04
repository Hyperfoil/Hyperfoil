package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.handlers.ByteStream;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.handlers.JsonParser;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class JsonStep extends BaseStep implements ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(JsonStep.class);

   private final ByteArrayParser byteArrayParser;
   private final String fromVar;
   private final String toVar;
   private final DataFormat format;

   private JsonStep(SerializableSupplier<Sequence> sequence, String fromVar, String query, String toVar, DataFormat format) {
      super(sequence);
      this.fromVar = fromVar;
      this.byteArrayParser = new ByteArrayParser(query);
      this.toVar = toVar;
      this.format = format;
   }

   @Override
   public boolean invoke(Session session) {
      Object object = session.getObject(fromVar);
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
   }

   public static class Builder extends BaseStepBuilder {
      private String fromVar;
      private String query;
      private String toVar;
      private DataFormat format = DataFormat.STRING;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public Builder query(String query) {
         this.query = query;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (fromVar == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'fromVar'");
         }
         if (query == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'query'");
         }
         if (toVar == null) {
            throw new BenchmarkDefinitionException("jsonQuery missing 'toVar'");
         }
         return Collections.singletonList(new JsonStep(sequence, fromVar, query, toVar, format));
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
            session.setObject(toVar, format.convert(((ByteArrayByteStream) data).array, offset, length));
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
