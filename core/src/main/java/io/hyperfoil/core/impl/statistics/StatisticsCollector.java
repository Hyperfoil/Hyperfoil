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
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StatisticsCollector implements Consumer<SessionStatistics> {
   private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
   private static final boolean trace = log.isTraceEnabled();

   private final Phase[] phases;
   protected IntObjectMap<Map<String, IntObjectMap<StatisticsSnapshot>>> aggregated = new IntObjectHashMap<>();

   public StatisticsCollector(Benchmark benchmark) {
      this.phases = benchmark.phasesById();
   }

   @Override
   public void accept(SessionStatistics statistics) {
      for (int i = 0; i < statistics.size(); ++i) {
         int phaseAndStepId = (statistics.phase(i).id() << 16) + statistics.step(i);

         Map<String, IntObjectMap<StatisticsSnapshot>> metricMap = aggregated.get(phaseAndStepId);
         if (metricMap == null) {
            metricMap = new HashMap<>();
            aggregated.put(phaseAndStepId, metricMap);
         }

         for (Map.Entry<String, Statistics> entry : statistics.stats(i).entrySet()) {
            String metric = entry.getKey();
            IntObjectMap<StatisticsSnapshot> snapshots = metricMap.computeIfAbsent(metric, k -> new IntObjectHashMap<>());
            entry.getValue().visitSnapshots(snapshot -> {
               assert snapshot.order >= 0;
               StatisticsSnapshot existing = snapshots.get(snapshot.order);
               if (existing == null) {
                  existing = new StatisticsSnapshot();
                  existing.order = snapshot.order;
                  snapshots.put(snapshot.order, existing);
               }
               snapshot.addInto(existing);
            });
         }
      }
   }

   public void visitStatistics(StatisticsConsumer consumer, CountDown countDown) {
      for (Iterator<Map.Entry<Integer, Map<String, IntObjectMap<StatisticsSnapshot>>>> it1 = aggregated.entrySet().iterator(); it1.hasNext(); ) {
         Map.Entry<Integer, Map<String, IntObjectMap<StatisticsSnapshot>>> entry = it1.next();
         int phaseAndStepId = entry.getKey();
         Map<String, IntObjectMap<StatisticsSnapshot>> metricMap = entry.getValue();

         for (Iterator<Map.Entry<String, IntObjectMap<StatisticsSnapshot>>> it2 = metricMap.entrySet().iterator(); it2.hasNext(); ) {
            Map.Entry<String, IntObjectMap<StatisticsSnapshot>> se = it2.next();
            String metric = se.getKey();
            IntObjectMap<StatisticsSnapshot> snapshots = se.getValue();

            for (Iterator<IntObjectMap.PrimitiveEntry<StatisticsSnapshot>> it3 = snapshots.entries().iterator(); it3.hasNext(); ) {
               IntObjectMap.PrimitiveEntry<StatisticsSnapshot> pe = it3.next();
               if (pe.value().requestCount == 0) {
                  it3.remove();
               } else {
                  consumer.accept(phases[phaseAndStepId >> 16], phaseAndStepId & 0xFFFF, metric, pe.value(), countDown);
                  pe.value().reset();
               }
            }

            if (snapshots.isEmpty()) {
               it2.remove();
            }
         }

         if (metricMap.isEmpty()) {
            it1.remove();
         }
      }
   }


   public interface StatisticsConsumer {
       void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown);
   }
}
