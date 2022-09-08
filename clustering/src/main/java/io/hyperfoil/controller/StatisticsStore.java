package io.hyperfoil.controller;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.core.util.LowHigh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StatisticsStore {
   static final double[] PERCENTILES = new double[]{ 0.5, 0.9, 0.99, 0.999, 0.9999 };
   private static final Comparator<RequestStats> REQUEST_STATS_COMPARATOR =
         Comparator.<RequestStats, Long>comparing(rs -> rs.summary.startTime)
               .thenComparing(rs -> rs.phase).thenComparing(rs -> rs.metric);

   private final Benchmark benchmark;
   final Map<Integer, Map<String, Data>> data = new HashMap<>();
   private final Consumer<SLA.Failure> failureHandler;
   final List<SLA.Failure> failures = new ArrayList<>();
   private final int maxFailures = 100;
   private final Map<Integer, SLA.Provider> slaProviders;
   final Map<String, SessionPoolStats> sessionPoolStats = new HashMap<>();
   final Map<String, Map<String, Map<String, List<ConnectionPoolStats>>>> connectionPoolStats = new HashMap<>();
   final Map<String, Map<String, String>> cpuUsage = new HashMap<>();

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler) {
      this.benchmark = benchmark;
      this.failureHandler = failureHandler;
      this.slaProviders = benchmark.steps()
            .filter(SLA.Provider.class::isInstance).map(SLA.Provider.class::cast)
            .collect(Collectors.toMap(SLA.Provider::id, Function.identity(), (s1, s2) -> {
               if (s1 != s2) {
                  throw new IllegalStateException();
               }
               return s1;
            }));
   }

   public boolean record(String agentName, int phaseId, int stepId, String metric, StatisticsSnapshot stats) {
      Map<String, Data> map = this.data.computeIfAbsent((phaseId << 16) + stepId, phaseStep -> new HashMap<>());
      Data data = map.get(metric);
      if (data == null) {
         long collectionPeriod = benchmark.statisticsCollectionPeriod();
         Phase phase = benchmark.phases().stream().filter(p -> p.id() == phaseId).findFirst().get();
         SLA[] sla;
         if (stepId != 0) {
            SLA.Provider slaProvider = slaProviders.get(stepId);
            sla = slaProvider == null ? null : slaProvider.sla();
         } else {
            sla = phase.customSlas.get(metric);
         }
         Map<SLA, Window> rings = sla == null ? Collections.emptyMap() :
               Stream.of(sla).filter(s -> s.window() > 0).collect(
                     Collectors.toMap(Function.identity(), s -> new Window((int) (s.window() / collectionPeriod))));
         SLA[] total = sla == null ? new SLA[0] : Stream.of(sla).filter(s -> s.window() <= 0).toArray(SLA[]::new);
         map.put(metric, data = new Data(this, phase.name, phase.isWarmup, stepId, metric, rings, total));
      }
      return data.record(agentName, stats);
   }

   public void addFailure(String phase, String metric, long startTimestamp, long endTimestamp, String cause) {
      StatisticsSnapshot statistics = new StatisticsSnapshot();
      statistics.histogram.setStartTimeStamp(startTimestamp);
      statistics.histogram.setEndTimeStamp(endTimestamp);
      failures.add(new SLA.Failure(null, phase, metric, statistics, cause));
   }

   public void completePhase(String phase) {
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            if (data.phase.equals(phase)) {
               data.completePhase();
            }
         }
      }
   }

   public void completeAll(Consumer<String> errorHandler) {
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            if (!data.isCompleted()) {
               String message = String.format("Data for %s/%d/%s were not completed when the phase terminated - was the data received after that?",
                     data.phase, data.stepId, data.metric);
               errorHandler.accept(message);
               data.completePhase();
            }
         }
      }
   }

   // When there's only few requests during the phase we could use too short interval for throughput calculation.
   // We cannot do this in completePhase() because that's invoked from the STATS feed and the overall completion
   // is notified from the RESPONSE feed.
   public void adjustPhaseTimestamps(String phase, long start, long completion) {
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            if (data.phase.equals(phase)) {
               data.total.histogram.setStartTimeStamp(Math.min(start, data.total.histogram.getStartTimeStamp()));
               data.total.histogram.setEndTimeStamp(Math.max(completion, data.total.histogram.getEndTimeStamp()));
            }
         }
      }
   }

   public boolean validateSlas() {
      return failures.isEmpty();
   }

   public List<RequestStats> recentSummary(long minValidTimestamp) {
      ArrayList<RequestStats> result = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            OptionalInt lastSequenceId = data.lastStats.values().stream()
                  .flatMapToInt(map -> map.keySet().stream().mapToInt(Integer::intValue)).max();
            if (lastSequenceId.isEmpty()) {
               continue;
            }
            // We'll use one id before the last one since the last one is likely not completed yet
            int penultimateId = lastSequenceId.getAsInt() - 1;
            StatisticsSnapshot sum = new StatisticsSnapshot();
            data.lastStats.values().stream().map(map -> map.get(penultimateId))
                  .filter(Objects::nonNull).forEach(sum::add);
            if (sum.isEmpty() || sum.histogram.getStartTimeStamp() < minValidTimestamp) {
               continue;
            }
            List<String> failures = this.failures.stream()
                  .filter(f -> f.phase().equals(data.phase) && (f.metric() == null || f.metric().equals(data.metric)))
                  .map(SLA.Failure::message).collect(Collectors.toList());
            result.add(new RequestStats(data.phase, data.stepId, data.metric, sum.summary(PERCENTILES), failures, data.isWarmup));
         }
      }
      result.sort(REQUEST_STATS_COMPARATOR);
      return result;
   }

   public List<RequestStats> totalSummary() {
      ArrayList<RequestStats> result = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            StatisticsSummary last = data.total.summary(PERCENTILES);
            List<String> failures = this.failures.stream()
                  .filter(f -> f.phase().equals(data.phase) && (f.metric() == null || f.metric().equals(data.metric)))
                  .map(SLA.Failure::message).collect(Collectors.toList());
            result.add(new RequestStats(data.phase, data.stepId, data.metric, last, failures, data.isWarmup));
         }
      }
      result.sort(REQUEST_STATS_COMPARATOR);
      return result;
   }

   public Histogram histogram(String phase, int stepId, String metric) {
      Data data = getData(phase, stepId, metric);
      if (data == null) {
         return null;
      }
      return HistogramConverter.convert(phase, metric, data.total.histogram);
   }

   public List<StatisticsSummary> series(String phase, int stepId, String metric) {
      Data data = getData(phase, stepId, metric);
      if (data == null) {
         return null;
      }
      return data.series;
   }

   private Data getData(String phase, int stepId, String metric) {
      int phaseId = benchmark.phases().stream().filter(p -> p.name.equals(phase)).mapToInt(p -> p.id).findFirst().orElse(-1);
      Map<String, Data> phaseStepData = data.get((phaseId << 16) + stepId);
      if (phaseStepData == null) {
         return null;
      }
      return phaseStepData.get(metric);
   }

   public void recordSessionStats(String address, long timestamp, String phase, int minSessions, int maxSessions) {
      SessionPoolStats sps = this.sessionPoolStats.computeIfAbsent(phase, p -> new SessionPoolStats());
      sps.records.computeIfAbsent(address, a -> new ArrayList<>()).add(new SessionPoolRecord(timestamp, minSessions, maxSessions));
   }

   public Map<String, Map<String, LowHigh>> recentSessionPoolSummary(long minValidTimestamp) {
      return sessionPoolSummary(records -> {
         SessionPoolRecord record = records.get(records.size() - 1);
         return record.timestamp >= minValidTimestamp ? record : null;
      });
   }

   public Map<String, Map<String, LowHigh>> totalSessionPoolSummary() {
      return sessionPoolSummary(records -> {
         int low = records.stream().mapToInt(r -> r.low).min().orElse(0);
         int high = records.stream().mapToInt(r -> r.high).max().orElse(0);
         return new LowHigh(low, high);
      });
   }

   private Map<String, Map<String, LowHigh>> sessionPoolSummary(Function<List<SessionPoolRecord>, LowHigh> function) {
      Map<String, Map<String, LowHigh>> result = new HashMap<>();
      for (Map.Entry<String, SessionPoolStats> phaseEntry : sessionPoolStats.entrySet()) {
         Map<String, LowHigh> addressSummary = new HashMap<>();
         for (Map.Entry<String, List<SessionPoolRecord>> addressEntry : phaseEntry.getValue().records.entrySet()) {
            List<SessionPoolRecord> records = addressEntry.getValue();
            if (records.isEmpty()) {
               continue;
            }
            LowHigh lohi = function.apply(records);
            if (lohi != null) {
               addressSummary.put(addressEntry.getKey(), lohi);
            }
         }
         if (!addressSummary.isEmpty()) {
            result.put(phaseEntry.getKey(), addressSummary);
         }
      }
      return result;
   }

   public void recordConnectionStats(String address, long timestamp, Map<String, Map<String, LowHigh>> stats) {
      for (var byAuthority : stats.entrySet()) {
         for (var byType : byAuthority.getValue().entrySet()) {
            var authorityData = connectionPoolStats.computeIfAbsent(byAuthority.getKey(), a -> new HashMap<>());
            var typeData = authorityData.computeIfAbsent(byType.getKey(), t -> new HashMap<>());
            var agentData = typeData.computeIfAbsent(address, a -> new ArrayList<>());
            LowHigh value = byType.getValue();
            agentData.add(new ConnectionPoolStats(timestamp, value.low, value.high));
         }
      }
   }

   public Map<String, Map<String, LowHigh>> recentConnectionsSummary() {
      Map<String, Map<String, LowHigh>> summary = new HashMap<>();
      long minTimestamp = System.currentTimeMillis() - 5000;
      for (var byAuthority : connectionPoolStats.entrySet()) {
         for (var byType : byAuthority.getValue().entrySet()) {
            // we will simply take last range from every agent
            if (byType.getValue().values().stream().anyMatch(list -> list.get(list.size() - 1).timestamp < minTimestamp)) {
               // the results are too old, we will ignore this
               continue;
            }
            LowHigh sum = byType.getValue().values().stream()
                  .map(list -> (LowHigh) list.get(list.size() - 1))
                  .reduce(LowHigh::sum).orElse(null);
            if (sum != null) {
               summary.computeIfAbsent(byAuthority.getKey(), a -> new HashMap<>()).put(byType.getKey(), sum);
            }
         }
      }
      return summary;
   }

   public Map<String, Map<String, LowHigh>> totalConnectionsSummary() {
      Map<String, Map<String, LowHigh>> summary = new HashMap<>();
      for (var byAuthority : connectionPoolStats.entrySet()) {
         for (var byType : byAuthority.getValue().entrySet()) {
            int maxSize = byType.getValue().values().stream().mapToInt(List::size).max().orElse(0);
            LowHigh total = null;
            for (int i = 0; i < maxSize; ++i) {
               int ii = i;
               total = LowHigh.combine(total, byType.getValue().values().stream()
                     .map(list -> ii < list.size() ? (LowHigh) list.get(ii) : null)
                     .reduce(LowHigh::sum).orElse(null));
            }
            if (total != null) {
               summary.computeIfAbsent(byAuthority.getKey(), a -> new HashMap<>()).put(byType.getKey(), total);
            }
         }
      }
      return summary;
   }

   public void recordCpuUsage(String phase, String agentName, String usage) {
      cpuUsage.computeIfAbsent(phase, p -> new HashMap<>()).putIfAbsent(agentName, usage);
   }

   public Map<String, Map<String, String>> cpuUsage() {
      return cpuUsage;
   }

   static final class Window {
      private final StatisticsSnapshot[] ring;
      private final StatisticsSnapshot sum = new StatisticsSnapshot();
      private int ptr = 0;

      Window(int size) {
         assert size > 0;
         ring = new StatisticsSnapshot[size];
      }

      void add(StatisticsSnapshot stats) {
         if (ring[ptr] != null) {
            sum.subtract(ring[ptr]);
         }
         ring[ptr] = stats;
         sum.add(stats);
         ptr = (ptr + 1) % ring.length;
      }

      public boolean isFull() {
         return ring[ptr] != null;
      }

      public StatisticsSnapshot current() {
         return sum;
      }
   }

   void addFailure(SLA.Failure failure) {
      if (failures.size() < maxFailures) {
         failures.add(failure);
      }
      failureHandler.accept(failure);
   }

   public List<Data> getData() {
      Data[] rtrn = data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(rtrn, Comparator.comparing((Data data) -> data.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));
      return Arrays.asList(rtrn);
   }

   void addData(int id, String metric, Data data) {
      this.data.computeIfAbsent(id, i -> new HashMap<>()).put(metric, data);
   }

   public List<SLA.Failure> getFailures() {
      return failures;
   }

   static class SessionPoolStats {
      Map<String, List<SessionPoolRecord>> records = new HashMap<>();

      LowHigh findMinMax() {
         int min = Integer.MAX_VALUE;
         int max = 0;
         List<Iterator<SessionPoolRecord>> iterators = records.values().stream()
               .map(List::iterator).collect(Collectors.toList());
         for (; ; ) {
            LowHigh combined = iterators.stream()
                  .filter(Iterator::hasNext).map(Iterator::next).map(LowHigh.class::cast)
                  .reduce(LowHigh::sum).orElse(null);
            if (combined == null) {
               break;
            }
            min = Math.min(min, combined.low);
            max = Math.max(max, combined.high);
         }
         return new LowHigh(min, max);
      }
   }

   static class SessionPoolRecord extends LowHigh {
      final long timestamp;

      SessionPoolRecord(long timestamp, int min, int max) {
         super(min, max);
         this.timestamp = timestamp;
      }
   }

   static class ConnectionPoolStats extends LowHigh {
      final long timestamp;

      ConnectionPoolStats(long timestamp, int low, int high) {
         super(low, high);
         this.timestamp = timestamp;
      }
   }
}
