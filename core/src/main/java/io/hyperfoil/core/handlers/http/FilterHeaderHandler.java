package io.hyperfoil.core.handlers.http;

import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.api.processor.HttpRequestProcessorBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.StringConditionBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.handlers.MultiProcessor;
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
            ByteBuf byteBuf = Util.string2byteBuf(value, request.connection().context().alloc().buffer());
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
      private StringConditionBuilder<?, Builder> header = new StringConditionBuilder<>(this).caseSensitive(false);
      private List<HttpRequestProcessorBuilder> processors = new ArrayList<>();

      @Override
      public FilterHeaderHandler build() {
         if (processors.isEmpty()) {
            throw new BenchmarkDefinitionException("Processor was not set!");
         }
         Processor processor = processors.size() == 1 ? processors.get(0).build(false) : new MultiProcessor(processors.stream().map(p -> p.build(false)).toArray(Processor[]::new));
         return new FilterHeaderHandler(header.buildPredicate(), processor);
      }

      public Builder processor(HttpRequestProcessorBuilder processor) {
         this.processors.add(processor);
         return this;
      }

      /**
       * Condition on the header name.
       *
       * @return Builder.
       */
      public StringConditionBuilder<?, Builder> header() {
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
