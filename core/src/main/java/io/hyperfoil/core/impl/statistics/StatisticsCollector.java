package io.hyperfoil.core.impl.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;

public class StatisticsCollector implements Consumer<SessionStatistics> {
   protected Map<Integer, Map<String, StatisticsSnapshot>> aggregated = new HashMap<>();

   @Override
   public void accept(SessionStatistics statistics) {
      for (int i = 0; i < statistics.size(); ++i) {
         Map<String, StatisticsSnapshot> snapshots = aggregated.computeIfAbsent(statistics.step(i), s -> new HashMap<>());
         for (Map.Entry<String, Statistics> entry : statistics.stats(i).entrySet()) {
            StatisticsSnapshot snapshot = snapshots.computeIfAbsent(entry.getKey(), k -> new StatisticsSnapshot());
            entry.getValue().addIntervalTo(snapshot);
         }
      }
   }

   public void visitStatistics(StatisticsConsumer consumer, CountDown countDown) {
       for (Map.Entry<Integer, Map<String, StatisticsSnapshot>> entry : aggregated.entrySet()) {
           int stepId = entry.getKey();
           for (Map.Entry<String, StatisticsSnapshot> se : entry.getValue().entrySet()) {
              StatisticsSnapshot snapshot = se.getValue();
              consumer.accept(stepId, se.getKey(), snapshot, countDown);
              snapshot.reset();
           }
       }
   }


   public interface StatisticsConsumer {
       void accept(int stepId, String name, StatisticsSnapshot snapshot, CountDown countDown);
   }
}
