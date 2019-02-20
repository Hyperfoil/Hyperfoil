package io.hyperfoil.core.impl.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;

public class StatisticsCollector implements BiConsumer<Phase, Map<String, Statistics>> {
   protected final Simulation simulation;
   protected Map<Phase, Map<String, StatisticsSnapshot>> aggregated = new HashMap<>();

   public StatisticsCollector(Simulation simulation) {
      this.simulation = simulation;
      for (Phase phase : simulation.phases()) {
         aggregated.computeIfAbsent(phase, name -> new HashMap<>());
      }
   }

   @Override
   public void accept(Phase phase, Map<String, Statistics> statistics) {
      Map<String, StatisticsSnapshot> snapshots = aggregated.get(phase);
      for (Map.Entry<String, Statistics> entry : statistics.entrySet()) {
         StatisticsSnapshot snapshot = snapshots.computeIfAbsent(entry.getKey(), k -> new StatisticsSnapshot());
         entry.getValue().addIntervalTo(snapshot);
      }
   }

   public void visitStatistics(StatisticsConsumer consumer, CountDown countDown) {
       for (Map.Entry<Phase, Map<String, StatisticsSnapshot>> entry : aggregated.entrySet()) {
           Phase phase = entry.getKey();
           for (Map.Entry<String, StatisticsSnapshot> se : entry.getValue().entrySet()) {
              StatisticsSnapshot snapshot = se.getValue();
              consumer.accept(phase, se.getKey(), snapshot, countDown);
              snapshot.reset();
           }
       }
   }


   public interface StatisticsConsumer {
       void accept(Phase phase, String name, StatisticsSnapshot snapshot, CountDown countDown);
   }
}
