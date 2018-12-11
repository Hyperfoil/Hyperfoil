package io.sailrocket.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.ServiceLoadedBuilder;
import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.StepBuilder;

/**
 * No functionality, just to demonstrate a service-loaded step.
 */
public class NoopStep implements Step {
   @Override
   public boolean invoke(Session session) {
      return true;
   }

   /**
    * The builder can be both service-loaded and used programmatically in {@link BaseSequenceBuilder#step(StepBuilder)}.
    */
   public static class Builder implements ServiceLoadedBuilder, StepBuilder {
      private final Consumer<List<Step>> buildTarget;

      public Builder(BaseSequenceBuilder parent) {
         buildTarget = null;
         parent.step(this);
      }

      public Builder(Consumer<List<Step>> buildTarget) {
         this.buildTarget = buildTarget;
      }

      @Override
      public void apply() {
         buildTarget.accept(build());
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new NoopStep());
      }
   }

   @MetaInfServices(Step.BuilderFactory.class)
   public static class BuilderFactory implements Step.BuilderFactory {
      @Override
      public String name() {
         return "noop";
      }

      @Override
      public ServiceLoadedBuilder newBuilder(Consumer<List<Step>> buildTarget, String param) {
         if (param != null) {
            throw new BenchmarkDefinitionException(NoopStep.class.getName() + " does not accept inline parameter");
         }
         return new Builder(buildTarget);
      }
   }

}
