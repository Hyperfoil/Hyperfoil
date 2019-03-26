package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
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
   public static class Builder extends BaseStepBuilder implements StepBuilder {
      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new NoopStep());
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
      public StepBuilder newBuilder(Locator locator, String param) {
         return new Builder(null);
      }
   }

}
