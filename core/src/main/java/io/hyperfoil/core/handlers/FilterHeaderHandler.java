package io.hyperfoil.core.handlers;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.CharSequenceCondition;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiPredicate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.kohsuke.MetaInfServices;

public class FilterHeaderHandler implements HeaderHandler, ResourceUtilizer {
   private final SerializableBiPredicate<Session, CharSequence> header;
   private final Processor processor;

   public FilterHeaderHandler(SerializableBiPredicate<Session, CharSequence> header, Processor processor) {
      this.header = header;
      this.processor = processor;
   }

   @Override
   public void beforeHeaders(HttpRequest request) {
      this.processor.before(request.session);
   }

   @Override
   public void afterHeaders(HttpRequest request) {
      this.processor.after(request.session);
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (this.header.test(request.session, header)) {
         if (value == null || value.length() == 0) {
            processor.process(request.session, Unpooled.EMPTY_BUFFER, 0, 0, true);
         } else {
            ByteBuf byteBuf = Util.string2byteBuf(value.toString(), request.connection().context().alloc().buffer());
            try {
               processor.process(request.session, byteBuf, byteBuf.readerIndex(), byteBuf.readableBytes(), true);
            } finally {
               byteBuf.release();
            }
         }
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, processor);
   }

   /**
    * Compares if the header name matches expression and invokes a processor with the value.
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("filter")
   public static class Builder implements HeaderHandler.Builder {
      private CharSequenceCondition.Builder<Builder> header = new CharSequenceCondition.Builder<>(this).caseSensitive(false);
      private HttpRequestProcessorBuilder processor;

      @Override
      public FilterHeaderHandler build() {
         if (processor == null) {
            throw new BenchmarkDefinitionException("Processor was not set!");
         }
         return new FilterHeaderHandler(header.build(), processor.build(false));
      }

      @Override
      public void prepareBuild() {
         processor.prepareBuild();
      }

      public Builder processor(HttpRequestProcessorBuilder processor) {
         if (this.processor != null) {
            throw new IllegalStateException("Processor already set.");
         }
         this.processor = processor;
         return this;
      }

      /**
       * Condition on the header name.
       *
       * @return Builder.
       */
      public CharSequenceCondition.Builder<Builder> header() {
         return header;
      }

      /**
       * Processor that will be invoked with the value (converted to ByteBuf).
       *
       * @return Builder;
       */
      public ServiceLoadedBuilderProvider<HttpRequestProcessorBuilder> processor() {
         return new ServiceLoadedBuilderProvider<>(HttpRequestProcessorBuilder.class, this::processor);
      }
   }
}
