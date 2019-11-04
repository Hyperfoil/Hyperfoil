package io.hyperfoil.api.processor;

import java.util.function.Function;

import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.config.Locator;
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
         return new ActionBuilderAdapter(builder);
      }
   }

   class ActionBuilderAdapter implements RequestProcessorBuilder {
      private final Action.Builder builder;

      public ActionBuilderAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public RequestProcessorBuilder copy(Locator locator) {
         return new ActionBuilderAdapter(builder.copy(locator));
      }

      @Override
      public Processor<Request> build() {
         return new Processor.ActionAdapter<>(builder.build());
      }
   }
}
