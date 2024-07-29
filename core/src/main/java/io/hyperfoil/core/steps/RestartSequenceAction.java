package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;

public class RestartSequenceAction implements Action, ResourceUtilizer {
   private final Session.ResourceKey<RestartSequenceStep.Trigger> triggerKey;

   public RestartSequenceAction(Session.ResourceKey<RestartSequenceStep.Trigger> triggerKey) {
      this.triggerKey = triggerKey;
   }

   @Override
   public void run(Session session) {
      session.getResource(triggerKey).restart = true;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(triggerKey, RestartSequenceStep.Trigger::new);
   }

   /**
    * Schedules a restart of this sequence.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("restartSequence")
   public static class Builder implements Action.Builder {
      private Session.ResourceKey<RestartSequenceStep.Trigger> triggerKey;

      @Override
      public void prepareBuild() {
         Locator locator = Locator.current();
         if (locator.step() instanceof BreakSequenceStep.Builder) {
            throw new BenchmarkDefinitionException(
                  "Restarting sequence this way from `breakSequence` does not work as this action adds subsequent step; use `conditional` instead.");
         }
         triggerKey = RestartSequenceStep.createTriggerKey();
         locator.sequence().insertAfter(locator).step(new RestartSequenceStep(triggerKey));
      }

      @Override
      public RestartSequenceAction build() {
         return new RestartSequenceAction(triggerKey);
      }
   }
}
