package io.hyperfoil.api.processor;

import java.util.function.Function;

import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.session.Action;

/**
 * Processors for HTTP requests.
 */
@IncludeBuilders({
      @IncludeBuilders.Conversion(from = RequestProcessorBuilder.class, adapter = HttpRequestProcessorBuilder.BuilderConverter.class),
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = HttpRequestProcessorBuilder.ActionBuilderConverter.class)
})
public interface HttpRequestProcessorBuilder extends Processor.Builder<HttpRequestProcessorBuilder> {

   static HttpRequestProcessorBuilder adapt(RequestProcessorBuilder builder) {
      return new RequestProcessorBuilderAdapter(builder);
   }

   class BuilderConverter implements Function<RequestProcessorBuilder, HttpRequestProcessorBuilder> {
      @Override
      public HttpRequestProcessorBuilder apply(RequestProcessorBuilder builder) {
         return new RequestProcessorBuilderAdapter(builder);
      }
   }

   class RequestProcessorBuilderAdapter implements HttpRequestProcessorBuilder {
      private final RequestProcessorBuilder builder;

      public RequestProcessorBuilderAdapter(RequestProcessorBuilder builder) {
         this.builder = builder;
      }

      @Override
      public RequestProcessorBuilderAdapter copy() {
         return new RequestProcessorBuilderAdapter(builder.copy());
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public Processor build(boolean fragmented) {
         return builder.build(fragmented);
      }
   }

   class ActionBuilderConverter implements Function<Action.Builder, HttpRequestProcessorBuilder> {
      @Override
      public HttpRequestProcessorBuilder apply(Action.Builder builder) {
         return new ActionBuilderAdapter(builder);
      }
   }

   class ActionBuilderAdapter implements HttpRequestProcessorBuilder {
      private final Action.Builder builder;

      public ActionBuilderAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public ActionBuilderAdapter copy() {
         return new ActionBuilderAdapter(builder.copy());
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public Processor build(boolean fragmented) {
         return new Processor.ActionAdapter(builder.build());
      }
   }
}
