package io.hyperfoil.api.processor;

import java.util.function.Function;

import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.session.Action;

/**
 * Processors for any type of request.
 */
@IncludeBuilders(
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = RequestProcessorBuilder.ActionBuilderConverter.class)
)
public interface RequestProcessorBuilder extends Processor.Builder<RequestProcessorBuilder> {

   static RequestProcessorBuilder adapt(Action.Builder builder) {
      return new ActionBuilderAdapter(builder);
   }

   class ActionBuilderConverter implements Function<Action.Builder, RequestProcessorBuilder> {
      @Override
      public RequestProcessorBuilder apply(Action.Builder builder) {
         return new ActionBuilderAdapter(builder);
      }
   }

   class ActionBuilderAdapter implements RequestProcessorBuilder {
      private final Action.Builder builder;

      public ActionBuilderAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public RequestProcessorBuilder copy() {
         return new ActionBuilderAdapter(builder.copy());
      }

      @Override
      public Processor build(boolean fragmented) {
         return new Processor.ActionAdapter(builder.build());
      }
   }
}
