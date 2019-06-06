package io.hyperfoil.core.impl.statistics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StatisticsCollector implements Consumer<SessionStatistics> {
   private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Phase[] phases;
   protected Map<Integer, Map<String, StatisticsSnapshot>> aggregated = new HashMap<>();

   public StatisticsCollector(Benchmark benchmark) {
      this.phases = benchmark.phasesById();
   }

   @Override
   public void accept(SessionStatistics statistics) {
      for (int i = 0; i < statistics.size(); ++i) {
         Map<String, StatisticsSnapshot> snapshots = aggregated.computeIfAbsent(
               (statistics.phase(i).id() << 16) + statistics.step(i), s -> new HashMap<>());
         for (Map.Entry<String, Statistics> entry : statistics.stats(i).entrySet()) {
            StatisticsSnapshot snapshot = snapshots.computeIfAbsent(entry.getKey(), k -> new StatisticsSnapshot());
            entry.getValue().addIntervalTo(snapshot);
         }
      }
   }

   public void visitStatistics(StatisticsConsumer consumer, CountDown countDown) {
      for (Iterator<Map.Entry<Integer, Map<String, StatisticsSnapshot>>> iterator = aggregated.entrySet().iterator(); iterator.hasNext(); ) {
         Map.Entry<Integer, Map<String, StatisticsSnapshot>> entry = iterator.next();
         int phaseAndStepId = entry.getKey();
         boolean empty = true;
         for (Map.Entry<String, StatisticsSnapshot> se : entry.getValue().entrySet()) {
            StatisticsSnapshot snapshot = se.getValue();
            consumer.accept(phases[phaseAndStepId >> 16], phaseAndStepId & 0xFFFF, se.getKey(), snapshot, countDown);
            empty = empty && snapshot.requestCount == 0;
            snapshot.reset();
         }
         if (empty) {
            // get rid of it when empty - the phase has probably ended
            iterator.remove();
         }
      }
   }


   public interface StatisticsConsumer {
       void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown);
   }
}
