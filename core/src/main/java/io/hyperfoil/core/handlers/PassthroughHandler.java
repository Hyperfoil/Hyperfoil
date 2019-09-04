package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.Processor;
import io.hyperfoil.api.http.BodyHandler;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.steps.ServiceLoadedBuilderProvider;
import io.hyperfoil.function.SerializableSupplier;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PassthroughHandler implements BodyHandler, ResourceUtilizer {
   private final Processor<? super HttpRequest> delegate;

   public PassthroughHandler(Processor<? super HttpRequest> delegate) {
      this.delegate = delegate;
   }

   @Override
   public void beforeData(HttpRequest request) {
      delegate.before(request);
   }

   @Override
   public void handleData(HttpRequest request, ByteBuf data) {
      delegate.process(request, data, data.readerIndex(), data.readableBytes(), false);
   }

   @Override
   public void afterData(HttpRequest request) {
      delegate.process(request, Unpooled.EMPTY_BUFFER, 0, 0, true);
      delegate.after(request);
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, delegate);
   }

   /**
    * Adapter sending the body to a processor.
    */
   public static class Builder implements BodyHandler.Builder {
      private Processor.Builder<? super HttpRequest> processor;
      private boolean defrag = true;

      public Builder processor(Processor.Builder<? super HttpRequest> processor) {
         this.processor = processor;
         return this;
      }

      /**
       * Processor that this handler delegates to.
       */
      public ServiceLoadedBuilderProvider<Processor.Builder<HttpRequest>, HttpRequest.ProcessorBuilderFactory> processor() {
         return new ServiceLoadedBuilderProvider<>(HttpRequest.ProcessorBuilderFactory.class, null, this::processor);
      }

      /**
       * Automatically defragment the body, passing the whole response in single chunk.
       */
      public Builder defrag(boolean defrag) {
         this.defrag = defrag;
         return this;
      }

      @Override
      public PassthroughHandler build(SerializableSupplier<? extends Step> step) {
         if (processor == null) {
            throw new BenchmarkDefinitionException("Processor must be set.");
         }
         Processor<? super HttpRequest> processor = this.processor.build();
         return new PassthroughHandler(defrag ? new DefragProcessor<>(processor) : processor);
      }
   }

   @MetaInfServices(BodyHandler.BuilderFactory.class)
   public static class BuilderFactory implements BodyHandler.BuilderFactory {
      @Override
      public String name() {
         return "passthrough";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public PassthroughHandler.Builder newBuilder(Locator locator, String param) {
         return new PassthroughHandler.Builder();
      }
   }
}
