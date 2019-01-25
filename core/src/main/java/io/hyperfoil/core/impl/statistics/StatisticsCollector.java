package io.hyperfoil.core.impl.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;

public class StatisticsCollector implements BiConsumer<Phase, Statistics[]> {
   protected final Simulation simulation;
   protected Map<Phase, StatisticsSnapshot[]> aggregated = new HashMap<>();

   public StatisticsCollector(Simulation simulation) {
      this.simulation = simulation;
      for (Phase phase : simulation.phases()) {
         StatisticsSnapshot[] snapshots = aggregated.computeIfAbsent(phase, name -> new StatisticsSnapshot[phase.scenario().sequences().length]);
         for (Sequence sequence : phase.scenario().sequences()) {
            snapshots[sequence.id()] = new StatisticsSnapshot();
         }
      }
   }


   @Override
   public void accept(Phase phase, Statistics[] statistics) {
      StatisticsSnapshot[] snapshots = aggregated.get(phase);
      for (int i = 0; i < statistics.length; i++) {
         Statistics s = statistics[i];
         StatisticsSnapshot snapshot = snapshots[i];
         s.addIntervalTo(snapshot);
      }
   }

   public void visitStatistics(StatisticsConsumer consumer, CountDown countDown) {
       for (Map.Entry<Phase, StatisticsSnapshot[]> entry : aggregated.entrySet()) {
           Phase phase = entry.getKey();
           Sequence[] sequences = phase.scenario().sequences();
           assert entry.getValue().length == sequences.length;
           for (int i = 0; i < sequences.length; ++i) {
              StatisticsSnapshot snapshot = entry.getValue()[i];
              consumer.accept(phase, sequences[i], snapshot, countDown);
              snapshot.reset();
           }
       }
   }


   public interface StatisticsConsumer {
       void accept(Phase phase, Sequence sequence, StatisticsSnapshot snapshot, CountDown countDown);
   }
}
