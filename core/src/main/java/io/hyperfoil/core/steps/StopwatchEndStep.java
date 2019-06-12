package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class StopwatchEndStep extends BaseStep {
   private final Access key;

   public StopwatchEndStep(SerializableSupplier<Sequence> sequence, Object key) {
      super(sequence);
      this.key = SessionFactory.access(key);
   }

   @Override
   public boolean invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) key.getObject(session);
      Statistics statistics = session.statistics(id(), sequence().name());
      statistics.incrementRequests(startTime.timestampMillis);
      statistics.recordResponse(startTime.timestampMillis, 0, now - startTime.timestampNanos);
      // TODO: record any request/response counts?
      return true;
   }
}
