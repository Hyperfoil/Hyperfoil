package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.ServiceLoadedBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.function.SerializableSupplier;

/**
 * No functionality, just to demonstrate a service-loaded step.
 */
public class NoopStep implements Step {
   @Override
   public boolean invoke(Session session) {
      return true;
   }

   /**
    * The builder can be both service-loaded and used programmatically in {@link BaseSequenceBuilder#stepBuilder(StepBuilder)}.
    */
   public static class Builder extends ServiceLoadedBuilder.Base<StepBuilder> implements StepBuilder {
      private BaseSequenceBuilder parent;

      /* Use this variant when constructing manually */
      public Builder(BaseSequenceBuilder parent) {
         super(null);
         this.parent = parent;
         parent.stepBuilder(this);
      }

      /* This variant is used when service-loading the step */
      public Builder(Consumer<StepBuilder> buildTarget) {
         super(buildTarget);
      }

      @Override
      protected StepBuilder build() {
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new NoopStep());
      }

      @Override
      public BaseSequenceBuilder endStep() {
         return parent;
      }
   }

   @MetaInfServices(StepBuilder.Factory.class)
   public static class BuilderFactory implements StepBuilder.Factory {
      @Override
      public String name() {
         return "noop";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public Builder newBuilder(StepBuilder stepBuilder, Consumer<StepBuilder> buildTarget, String param) {
         return new Builder(buildTarget);
      }
   }

}
