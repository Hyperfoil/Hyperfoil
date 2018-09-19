package io.sailrocket.core.steps;

import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.api.config.Step;

public class StopwatchEndStep implements Step {
   private final Object key;

   public StopwatchEndStep(Object key) {
      this.key = key;
   }

   @Override
   public void invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) session.getObject(key);
      Statistics statistics = session.currentSequence().statistics(session);
      statistics.recordValue(now - startTime.timestamp);
      // TODO: record any request/response counts?
   }
}
