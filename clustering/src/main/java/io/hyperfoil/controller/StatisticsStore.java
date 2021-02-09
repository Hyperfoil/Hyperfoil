package io.hyperfoil.controller;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.controller.model.CustomStats;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.core.builders.SLA;
import io.hyperfoil.core.util.LowHigh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

   public void record(String address, int phaseId, int stepId, String metric, StatisticsSnapshot stats) {
      Map<String, Data> map = this.data.computeIfAbsent((phaseId << 16) + stepId, phaseStep -> new HashMap<>());
      Data data = map.get(metric);
      if (data == null) {
         long collectionPeriod = benchmark.statisticsCollectionPeriod();
         SLA.Provider slaProvider = slaProviders.get(stepId);
         Map<SLA, Window> rings = slaProvider == null || slaProvider.sla() == null ? Collections.emptyMap() :
               Stream.of(slaProvider.sla()).filter(sla -> sla.window() > 0).collect(
                     Collectors.toMap(Function.identity(),
                           sla -> new Window((int) (sla.window() / collectionPeriod))));
         SLA[] total = slaProvider == null || slaProvider.sla() == null ? new SLA[0] : Stream.of(slaProvider.sla())
               .filter(sla -> sla.window() <= 0).toArray(SLA[]::new);
         String phase = benchmark.phases().stream().filter(p -> p.id() == phaseId).findFirst().get().name();
         map.put(metric, data = new Data(this, phase, stepId, metric, rings, total));
      }
      data.record(address, stats);
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
            if (!lastSequenceId.isPresent()) {
               continue;
            }
            // We'll use one id before the last one since the last one is likely not completed yet
            int penultimateId = lastSequenceId.getAsInt() - 1;
            StatisticsSnapshot sum = new StatisticsSnapshot();
            data.lastStats.values().stream().map(map -> map.get(penultimateId))
                  .filter(snapshot -> snapshot != null)
                  .forEach(snapshot -> snapshot.addInto(sum));
            if (sum.isEmpty() || sum.histogram.getStartTimeStamp() < minValidTimestamp) {
               continue;
            }
            List<String> failures = this.failures.stream()
                  .filter(f -> f.phase().equals(data.phase) && (f.metric() == null || f.metric().equals(data.metric)))
                  .map(f -> f.message()).collect(Collectors.toList());
            result.add(new RequestStats(data.phase, data.stepId, data.metric, sum.summary(PERCENTILES), failures));
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
                  .map(f -> f.message()).collect(Collectors.toList());
            result.add(new RequestStats(data.phase, data.stepId, data.metric, last, failures));
         }
      }
      result.sort(REQUEST_STATS_COMPARATOR);
      return result;
   }

   public List<CustomStats> customStats() {
      ArrayList<CustomStats> list = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            for (Map.Entry<Object, CustomValue> entry : data.total.custom.entrySet()) {
               list.add(new CustomStats(data.phase, data.stepId, data.metric, entry.getKey().toString(), entry.getValue().toString()));
            }
         }
      }
      Comparator<CustomStats> c = Comparator.comparing(s -> s.phase);
      c = c.thenComparing(s -> s.stepId).thenComparing(s -> s.metric).thenComparing(s -> s.customName);
      Collections.sort(list, c);
      return list;
   }

   public Histogram histogram(String phase, int stepId, String metric) {
      int phaseId = benchmark.phases().stream().filter(p -> p.name.equals(phase)).mapToInt(p -> p.id).findFirst().orElse(-1);
      Map<String, Data> phaseStepData = data.get((phaseId << 16) + stepId);
      if (phaseStepData == null) {
         return null;
      }
      Data data = phaseStepData.get(metric);
      if (data == null) {
         return null;
      }
      return HistogramConverter.convert(phase, metric, data.total.histogram);
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
            ring[ptr].subtractFrom(sum);
         }
         ring[ptr] = stats;
         stats.addInto(sum);
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

      private SessionPoolRecord(long timestamp, int min, int max) {
         super(min, max);
         this.timestamp = timestamp;
      }
   }
}
