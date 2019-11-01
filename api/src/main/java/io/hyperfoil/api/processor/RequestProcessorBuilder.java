package io.hyperfoil.api.processor;

import java.util.function.Function;

import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Action;

/**
 * Processors for any type of request.
 */
@IncludeBuilders(
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = RequestProcessorBuilder.ActionBuilderConverter.class)
)
public interface RequestProcessorBuilder extends Processor.Builder<Request, RequestProcessorBuilder> {

   class ActionBuilderConverter implements Function<Action.Builder, RequestProcessorBuilder> {
      @Override
      public RequestProcessorBuilder apply(Action.Builder builder) {
         return () -> new Processor.ActionAdapter<>(builder.build());
      }
   }
}
