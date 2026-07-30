package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;

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
   public static class Builder extends BaseStepBuilder<Builder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(new NoopStep());
      }
   }
}
