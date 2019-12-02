package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.session.SessionFactory;

public class StopwatchEndStep extends BaseStep {
   private final Access key;
   private final String metrics;

   public StopwatchEndStep(Object key, String metrics) {
      this.key = SessionFactory.access(key);
      this.metrics = metrics;
   }

   @Override
   public boolean invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) key.getObject(session);
      Statistics statistics = session.statistics(id(), metrics);
      statistics.incrementRequests(startTime.timestampMillis);
      statistics.recordResponse(startTime.timestampMillis, 0, now - startTime.timestampNanos);
      // TODO: record any request/response counts?
      return true;
   }
}
