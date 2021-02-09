package io.hyperfoil.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.builders.SLA;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

final class Data {
   private static final Logger log = LoggerFactory.getLogger(Data.class);

   // When we receive snapshot with order #N we will attempt to compact agent snapshots #(N-60)
   // We are delaying this because the statistics for outlier may come with a significant delay
   private static final int MERGE_DELAY = 60;

   private final StatisticsStore statisticsStore;
   final String phase;
   final int stepId;
   final String metric;
   // for reporting
   final StatisticsSnapshot total = new StatisticsSnapshot();
   final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
   final Map<String, IntObjectMap<StatisticsSnapshot>> lastStats = new HashMap<>();
   final List<StatisticsSummary> series = new ArrayList<>();
   final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
   // floating statistics for SLAs
   private final Map<SLA, StatisticsStore.Window> windowSlas;
   private final SLA[] totalSlas;
   private int highestSequenceId = 0;
   private boolean completed;

   Data(StatisticsStore statisticsStore, String phase, int stepId, String metric, Map<SLA, StatisticsStore.Window> periodSlas, SLA[] totalSlas) {
      this.statisticsStore = statisticsStore;
      this.phase = phase;
      this.stepId = stepId;
      this.metric = metric;
      this.windowSlas = periodSlas;
      this.totalSlas = totalSlas;
   }

   void record(String address, StatisticsSnapshot stats) {
      stats.addInto(total);
      stats.addInto(perAgent.computeIfAbsent(address, a -> new StatisticsSnapshot()));
      IntObjectMap<StatisticsSnapshot> partialSnapshots = lastStats.computeIfAbsent(address, a -> new IntObjectHashMap<>());
      StatisticsSnapshot partialSnapshot = partialSnapshots.get(stats.sequenceId);
      if (partialSnapshot == null) {
         partialSnapshots.put(stats.sequenceId, stats);
      } else {
         stats.addInto(partialSnapshot);
      }
      while (stats.sequenceId > highestSequenceId) {
         ++highestSequenceId;
         int mergedSequenceId = highestSequenceId - MERGE_DELAY;
         if (mergedSequenceId < 0) {
            continue;
         }
         mergeSnapshots(mergedSequenceId);
      }
   }

   private void mergeSnapshots(int sequenceId) {
      StatisticsSnapshot sum = new StatisticsSnapshot();
      for (Map.Entry<String, IntObjectMap<StatisticsSnapshot>> entry : lastStats.entrySet()) {
         StatisticsSnapshot snapshot = entry.getValue().remove(sequenceId);
         if (snapshot != null) {
            snapshot.addInto(sum);
            agentSeries.computeIfAbsent(entry.getKey(), a -> new ArrayList<>()).add(snapshot.summary(StatisticsStore.PERCENTILES));
         }
      }
      if (!sum.isEmpty()) {
         series.add(sum.summary(StatisticsStore.PERCENTILES));
      }
      for (Map.Entry<SLA, StatisticsStore.Window> entry : windowSlas.entrySet()) {
         SLA sla = entry.getKey();
         StatisticsStore.Window window = entry.getValue();

         window.add(sum);

         // If we haven't filled full window the SLA won't be validated
         SLA.Failure failure = sla.validate(phase, metric, window.current());
         if (window.isFull() && failure != null) {
            statisticsStore.addFailure(failure);
         }
      }
   }

   void completePhase() {
      for (int i = Math.max(0, highestSequenceId - MERGE_DELAY); i <= highestSequenceId; ++i) {
         mergeSnapshots(i);
      }
      // Just sanity checks
      if (series.stream().mapToLong(ss -> ss.requestCount).sum() != total.requestCount) {
         log.error("We lost some data (series) in phase {} metric {}", phase, metric);
      }
      if (agentSeries.values().stream().flatMap(List::stream).mapToLong(ss -> ss.requestCount).sum() != total.requestCount) {
         log.error("We lost some data (agent series) in phase {} metric {}", phase, metric);
      }
      if (perAgent.values().stream().mapToLong(ss -> ss.requestCount).sum() != total.requestCount) {
         log.error("We lost some data (per agent) in phase {} metric {}", phase, metric);
      }
      log.trace("Validating failures for " + phase + "/" + metric);
      for (SLA sla : totalSlas) {
         SLA.Failure failure = sla.validate(phase, metric, total);
         if (failure != null) {
            statisticsStore.addFailure(failure);
         }
      }
      completed = true;
   }

   boolean isCompleted() {
      return completed;
   }
}
