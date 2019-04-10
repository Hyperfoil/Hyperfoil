package io.hyperfoil.core.handlers;

import java.util.function.Consumer;
import java.util.function.Function;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.core.steps.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.BodyHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class JsonHandler extends JsonParser<Request>
      implements BodyHandler, ResourceUtilizer, Session.ResourceKey<JsonHandler.Context> {
   private final Processor<Request> processor;

   public JsonHandler(String query, Processor<Request> processor) {
      super(query.trim());
      this.processor = processor;

   }

   @Override
   public void beforeData(HttpRequest request) {
      processor.before(request);
   }

   @Override
   public void handleData(HttpRequest request, ByteBuf data) {
      Context ctx = request.session.getResource(this);
      ctx.parse(ctx.wrap(data), request);
   }

   @Override
   public void afterData(HttpRequest request) {
      processor.after(request);
      Context ctx = request.session.getResource(this);
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
      session.declareResource(this, new Context());
      if (processor instanceof ResourceUtilizer) {
         ((ResourceUtilizer) processor).reserve(session);
      }
   }

   @Override
   protected void fireMatch(JsonParser<Request>.Context context, Request request, ByteStream data, int offset, int length, boolean isLastPart) {
      processor.process(request, ((ByteBufByteStream) data).buffer, offset, length, isLastPart);
   }

   public class Context extends JsonParser<Request>.Context {
      ByteBufByteStream actualStream;

      Context() {
         super(self -> new ByteBufByteStream(null, self::release));
         actualStream = new ByteBufByteStream(this::retain, null);
      }

      public ByteStream wrap(ByteBuf data) {
         actualStream.buffer = data;
         return actualStream;
      }
   }

   public static class Builder implements BodyHandler.Builder {
      private final Locator locator;
      private String query;
      private Processor.Builder<Request> processor;

      public Builder(Locator locator) {
         this.locator = locator;
      }

      @Override
      public JsonHandler build() {
         return new JsonHandler(query, processor.build());
      }

      public Builder query(String query) {
         this.query = query;
         return this;
      }

      public Builder arrayVar(String varAndSize) {
         return processor(ArrayRecorder.arrayVar(varAndSize));
      }

      public Builder processor(Processor<Request> processor) {
         return processor(() -> processor);
      }

      public Builder processor(Processor.Builder<Request> processor) {
         this.processor = processor;
         return this;
      }

      public ServiceLoadedBuilderProvider<Processor.Builder<Request>, Request.ProcessorBuilderFactory> processor() {
         return new ServiceLoadedBuilderProvider<>(Request.ProcessorBuilderFactory.class, locator, this::processor);
      }

      @Override
      public BodyHandler.Builder copy(Locator locator) {
         return new Builder(locator).query(query).processor(processor);
      }
   }

   @MetaInfServices(BodyHandler.BuilderFactory.class)
   public static class Factory implements BodyHandler.BuilderFactory {
      @Override
      public String name() {
         return "json";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         if (param != null && !param.isEmpty()) {
            String query;
            String var;
            if (param.indexOf("->") >= 0) {
               String[] parts = param.split("->");
               query = parts[0];
               var = parts[1];
            } else if (param.indexOf("<-") >= 0) {
               String[] parts = param.split("->");
               query = parts[1];
               var = parts[0];
            } else {
               throw new BenchmarkDefinitionException("Cannot parse json handler specification: '" + param + "', use 'query -> var' or 'var <- query'");
            }
            return new Builder(locator).query(query.trim()).processor(new DefragProcessor<>(new SimpleRecorder(var.trim())));
         }
         return new Builder(locator);
      }
   }

   static class ByteBufByteStream implements ByteStream {
      private final Function<ByteStream, ByteStream> retain;
      private final Consumer<ByteStream> release;
      private ByteBuf buffer;

      ByteBufByteStream(Function<ByteStream, ByteStream> retain, Consumer<ByteStream> release) {
         this.retain = retain;
         this.release = release;
      }

      @Override
      public boolean isReadable() {
         return buffer.isReadable();
      }

      @Override
      public byte readByte() {
         return buffer.readByte();
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
         return buffer.readerIndex();
      }

      @Override
      public void release() {
         buffer.release();
         buffer = null;
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
         buffer = null;
      }
   }
}
