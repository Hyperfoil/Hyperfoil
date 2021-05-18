package io.hyperfoil.http.handlers;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.StringConditionBuilder;
import io.hyperfoil.core.handlers.MultiProcessor;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiPredicate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.kohsuke.MetaInfServices;

public class FilterHeaderHandler implements HeaderHandler {
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

   /**
    * Compares if the header name matches expression and invokes a processor with the value.
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("filter")
   public static class Builder implements HeaderHandler.Builder {
      private StringConditionBuilder<?, Builder> header = new StringConditionBuilder<>(this).caseSensitive(false);
      @Embed
      public MultiProcessor.Builder<?> processors = new MultiProcessor.Builder<>();

      @Override
      public FilterHeaderHandler build() {
         if (processors.isEmpty()) {
            throw new BenchmarkDefinitionException("Processor was not set!");
         }
         Processor processor = processors.buildSingle(false);
         return new FilterHeaderHandler(header.buildPredicate(), processor);
      }

      /**
       * Condition on the header name.
       *
       * @return Builder.
       */
      public StringConditionBuilder<?, Builder> header() {
         return header;
      }

      public Builder processor(Processor.Builder processor) {
         processors.processor(processor);
         return this;
      }
   }
}
