package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
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
    * Does nothing. Only for demonstration purposes.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("noop")
   public static class Builder extends BaseStepBuilder implements StepBuilder {
      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new NoopStep());
      }
   }
}
