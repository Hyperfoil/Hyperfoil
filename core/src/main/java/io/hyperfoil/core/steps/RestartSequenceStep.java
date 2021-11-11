package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;

public class RestartSequenceStep implements Step {
   private final Session.ResourceKey<Trigger> triggerKey;

   public static Session.ResourceKey<Trigger> createTriggerKey() {
      // we cannot call this directly from any builder because it would capture reference to instantiating class
      return new Session.ResourceKey<>() {};
   }

   public RestartSequenceStep(Session.ResourceKey<Trigger> triggerKey) {
      this.triggerKey = triggerKey;
   }

   @Override
   public boolean invoke(Session session) {
      if (triggerKey != null) {
         Trigger trigger = session.getResource(triggerKey);
         if (!trigger.restart) {
            // restart not triggered, continuing
            return true;
         } else {
            trigger.restart = false;
         }
      }
      session.currentSequence().restart(session);
      return true;
   }

   /**
    * Restarts current sequence from beginning.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("restartSequence")
   public static class Builder extends BaseStepBuilder<Builder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(new RestartSequenceStep(null));
      }
   }

   public static class Trigger implements Session.Resource {
      public boolean restart;
   }
}
