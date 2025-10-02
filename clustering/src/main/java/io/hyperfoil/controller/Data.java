package io.hyperfoil.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.HdrHistogram.Histogram;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

final class Data {
   private static final Logger log = LogManager.getLogger(Data.class);

   // When we receive snapshot with order #N we will attempt to compact agent snapshots #(N-60)
   // We are delaying this because the statistics for outlier may come with a significant delay
   private static final int MERGE_DELAY = 60;

   private final StatisticsStore statisticsStore;
   final String phase;
   final boolean isWarmup;
   final int stepId;
   final String metric;
   // for reporting
   private final Boolean trackIntervalHistograms;
   final StatisticsSnapshot total = new StatisticsSnapshot();
   final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
   final Map<String, IntObjectMap<StatisticsSnapshot>> lastStats = new HashMap<>();
   final List<StatisticsSummary> series = new ArrayList<>();
   final Map<String, List<Histogram>> intervalHistogramsPerAgent = new HashMap<>();;
   final List<Histogram> intervalHistograms = new ArrayList<>();
   final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
   // floating statistics for SLAs
   private final Map<SLA, StatisticsStore.Window> windowSlas;
   private final SLA[] totalSlas;
   private int highestSampleId = 0;
   private boolean completed;

   Data(StatisticsStore statisticsStore, String phase, boolean isWarmup, int stepId, String metric,
         Map<SLA, StatisticsStore.Window> periodSlas, SLA[] totalSlas, Boolean trackIntervalHistograms) {
      this.statisticsStore = statisticsStore;
      this.phase = phase;
      this.isWarmup = isWarmup;
      this.stepId = stepId;
      this.metric = metric;
      this.windowSlas = periodSlas;
      this.totalSlas = totalSlas;
      this.trackIntervalHistograms = trackIntervalHistograms;
   }

   boolean record(String agentName, StatisticsSnapshot stats) {
      if (completed) {
         log.warn("Ignoring statistics for completed {}/{}/{} (from {}, {} requests)", phase, stepId, metric, agentName,
               stats.requestCount);
         return false;
      }
      total.add(stats);
      perAgent.computeIfAbsent(agentName, a -> new StatisticsSnapshot()).add(stats);
      IntObjectMap<StatisticsSnapshot> partialSnapshots = lastStats.computeIfAbsent(agentName, a -> new IntObjectHashMap<>());
      StatisticsSnapshot partialSnapshot = partialSnapshots.get(stats.sampleId);
      if (partialSnapshot == null) {
         partialSnapshots.put(stats.sampleId, stats);
      } else {
         partialSnapshot.add(stats);
      }
      while (stats.sampleId > highestSampleId) {
         ++highestSampleId;
         int mergedSequenceId = highestSampleId - MERGE_DELAY;
         if (mergedSequenceId < 0) {
            continue;
         }
         mergeSnapshots(mergedSequenceId);
      }
      return true;
   }

   private void mergeSnapshots(int sequenceId) {
      StatisticsSnapshot sum = new StatisticsSnapshot();
      for (Map.Entry<String, IntObjectMap<StatisticsSnapshot>> entry : lastStats.entrySet()) {
         StatisticsSnapshot snapshot = entry.getValue().remove(sequenceId);
         if (snapshot != null) {
            sum.add(snapshot);
            agentSeries.computeIfAbsent(entry.getKey(), a -> new ArrayList<>())
                  .add(snapshot.summary(StatisticsStore.PERCENTILES));
            if (trackIntervalHistograms) {
               // interval histograms per agent
               intervalHistogramsPerAgent.computeIfAbsent(entry.getKey(), a -> new ArrayList<>())
                     .add(snapshot.histogram);
            }
         }
      }
      if (!sum.isEmpty()) {
         series.add(sum.summary(StatisticsStore.PERCENTILES));
         if (trackIntervalHistograms) {
            intervalHistograms.add(sum.histogram);
         }
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
      for (int i = Math.max(0, highestSampleId - MERGE_DELAY); i <= highestSampleId; ++i) {
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
      log.trace("Validating failures for {}/{}", phase, metric);
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
