package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.config.Step;

public class StopwatchEndStep implements Step {
   private final Object key;

   public StopwatchEndStep(Object key) {
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) session.getObject(key);
      Statistics statistics = session.currentSequence().statistics(session);
      statistics.recordResponse(0, now - startTime.timestamp);
      // TODO: record any request/response counts?
      return true;
   }
}
