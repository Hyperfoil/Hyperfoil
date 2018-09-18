package io.sailrocket.core.impl.statistics;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import io.sailrocket.api.Phase;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.Session;
import io.sailrocket.api.Simulation;
import io.sailrocket.api.Statistics;
import io.sailrocket.api.StatisticsSnapshot;

public class StatisticsCollector implements Consumer<Session> {
   protected final Simulation simulation;
   protected final boolean resetBefore;
   protected Map<Phase, StatisticsSnapshot[]> aggregated = new HashMap<>();

   public StatisticsCollector(Simulation simulation, boolean resetBefore) {
      this.simulation = simulation;
      this.resetBefore = resetBefore;
      for (Phase phase : simulation.phases()) {
         StatisticsSnapshot[] snapshots = aggregated.computeIfAbsent(phase, name -> new StatisticsSnapshot[phase.scenario().sequences().length]);
         for (Sequence sequence : phase.scenario().sequences()) {
            snapshots[sequence.id()] = new StatisticsSnapshot();
         }
      }
   }

   @Override
   public void accept(Session session) {
      StatisticsSnapshot[] snapshots = aggregated.get(session.phase());
      for (Sequence sequence : session.phase().scenario().sequences()) {
         Statistics statistics = session.statistics(sequence.id());
         StatisticsSnapshot snapshot = snapshots[sequence.id()];
         if (resetBefore) {
            statistics.moveIntervalTo(snapshot);
         } else {
            statistics.addIntervalTo(snapshot);
         }
      }
   }

   public void visitStatistics(StatisticsConsumer consumer) {
       for (Map.Entry<Phase, StatisticsSnapshot[]> entry : aggregated.entrySet()) {
           Phase phase = entry.getKey();
           Sequence[] sequences = phase.scenario().sequences();
           assert entry.getValue().length == sequences.length;
           for (int i = 0; i < sequences.length; ++i) {
              StatisticsSnapshot snapshot = entry.getValue()[i];
              if (consumer.accept(phase, sequences[i], snapshot)) {
                 snapshot.reset();
              }
           }
       }
   }

   public interface StatisticsConsumer {
       boolean accept(Phase phase, Sequence sequence, StatisticsSnapshot snapshot);
   }
}
