package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;

public class StopwatchEndStep extends StatisticsStep {
   private final ReadAccess key;
   private final String metrics;

   public StopwatchEndStep(ReadAccess key, String metrics) {
      super(StatisticsStep.nextId());
      this.key = key;
      this.metrics = metrics;
   }

   @Override
   public boolean invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) key.getObject(session);
      Statistics statistics = session.statistics(id(), metrics);
      statistics.incrementRequests(startTime.timestampMillis);
      statistics.recordResponse(startTime.timestampMillis, now - startTime.timestampNanos);
      // TODO: record any request/response counts?
      return true;
   }
}
