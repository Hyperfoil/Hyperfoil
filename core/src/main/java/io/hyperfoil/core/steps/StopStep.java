package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;

public class StopStep implements Step {
   @Override
   public boolean invoke(Session session) {
      session.stop();
      return true;
   }

   /**
    * Immediately stop the user session (break all running sequences).
    */
   @MetaInfServices(StepBuilder.class)
   @Name("stop")
   public static class Builder implements StepBuilder<Builder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(new StopStep());
      }
   }
}
