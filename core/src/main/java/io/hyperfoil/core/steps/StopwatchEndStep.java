package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.function.SerializableSupplier;

public class StopwatchEndStep extends BaseStep {
   private final Object key;

   public StopwatchEndStep(SerializableSupplier<Sequence> sequence, Object key) {
      super(sequence);
      this.key = key;
   }

   @Override
   public boolean invoke(Session session) {
      long now = System.nanoTime();
      StopwatchBeginStep.StartTime startTime = (StopwatchBeginStep.StartTime) session.getObject(key);
      Statistics statistics = session.statistics(sequence().name());
      statistics.recordResponse(0, now - startTime.timestamp);
      // TODO: record any request/response counts?
      return true;
   }
}
