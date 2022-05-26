package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Session;

public class BreakIfFinishedStep implements Step {
   private static final Logger log = LogManager.getLogger(BreakIfFinishedStep.class);

   @Override
   public boolean invoke(Session session) {
      if (session.phase().status().isFinished()) {
         log.trace("#{} interrupting sequence {} as {} is finished ({})", session.uniqueId(),
               session.currentSequence(), session.phase().definition().name(), session.phase().status());
         session.currentSequence().breakSequence(session);
      }
      return true;
   }

   /**
    * Stop execution of current sequence if the phase is in finished state.
    * <p>
    * This is useful for a long-running (looping) sequence that should not extend the duration of its phase.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("breakIfFinished")
   public static class Builder implements StepBuilder<Builder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(new BreakIfFinishedStep());
      }
   }
}
