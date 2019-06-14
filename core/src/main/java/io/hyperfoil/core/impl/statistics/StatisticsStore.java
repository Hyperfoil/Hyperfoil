package io.hyperfoil.core.impl.statistics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.client.Client;
import io.hyperfoil.core.util.LowHigh;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

public class StatisticsStore {
   private static final double[] PERCENTILES = new double[]{0.5, 0.9, 0.99, 0.999, 0.9999};
   // When we receive snapshot with order #N we will attempt to compact agent snapshots #(N-60)
   // We are delaying this because the statistics for outlier may come with a significant delay
   private static final int MERGE_DELAY = 60;

   private final Benchmark benchmark;
   private final int numAgents;
   private final Map<Integer, Map<String, Data>> data = new HashMap<>();
   private final Consumer<SLA.Failure> failureHandler;
   private final double[] percentiles;
   private final List<SLA.Failure> failures = new ArrayList<>();
   private final int maxFailures = 100;
   private final Map<Integer, SLA.Provider> slaProviders;
   private final Map<String, SessionPoolStats> sessionPoolStats = new HashMap<>();

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler, double[] percentiles) {
      this.benchmark = benchmark;
      this.numAgents = Math.max(benchmark.agents().length, 1);
      this.failureHandler = failureHandler;
      this.percentiles = percentiles;
      this.slaProviders = benchmark.steps()
            .filter(SLA.Provider.class::isInstance).map(SLA.Provider.class::cast)
            .collect(Collectors.toMap(SLA.Provider::id, Function.identity(), (s1, s2) -> {
               if (s1 != s2) throw new IllegalStateException();
               return s1;
            }));
   }

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler) {
      this(benchmark, failureHandler, PERCENTILES);
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
         map.put(metric, data = new Data(phase, stepId, metric, rings, total));
      }
      data.record(address, stats);
   }

   public void persist(Path dir) throws IOException {
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      Data[] sorted = this.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, (d1, d2) -> {
         int cmp = d1.phase.compareTo(d2.phase);
         if (cmp != 0) return cmp;
         cmp = d1.metric.compareTo(d2.metric);
         if (cmp != 0) return cmp;
         return Integer.compare(d1.stepId, d2.stepId);
      });

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Metric,Start,End,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println(",MinSessions,MaxSessions");
         for (Data data : sorted) {
            writer.print(data.phase);
            writer.print(',');
            writer.print(data.metric);
            writer.print(',');
            writer.print(data.total.histogram.getStartTimeStamp());
            writer.print(',');
            writer.print(data.total.histogram.getEndTimeStamp());
            writer.print(',');
            data.total.summary(percentiles).printTo(writer);

            SessionPoolStats sps = this.sessionPoolStats.get(data.phase);
            if (sps == null) {
               writer.print(",,");
            } else {
               LowHigh minMax = sps.findMinMax();
               writer.print(',');
               writer.print(minMax.low);
               writer.print(',');
               writer.print(minMax.high);
            }
            writer.println();
         }
      }
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.metric) + "." + data.stepId;
            persistHistogramAndSeries(filePrefix, data.total, data.series);
         }
      }
      String[] agents = this.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Metric,Start,End,");
            StatisticsSummary.printHeader(writer, percentiles);
            writer.println(",MinSessions,MaxSessions");
            for (Data data : sorted) {
               StatisticsSnapshot agentStats = data.perAgent.get(agent);
               if (agentStats == null) {
                  continue;
               }
               writer.print(data.phase);
               writer.print(',');
               writer.print(data.metric);
               writer.print(',');
               writer.print(data.total.histogram.getStartTimeStamp());
               writer.print(',');
               writer.print(data.total.histogram.getEndTimeStamp());
               writer.print(',');
               agentStats.summary(percentiles).printTo(writer);

               SessionPoolStats sps = this.sessionPoolStats.get(data.phase);
               if (sps == null || sps.records.get(agent) == null) {
                  writer.print(",,");
               } else {
                  LowHigh lohi = sps.records.get(agent).stream().map(LowHigh.class::cast)
                        .reduce(LowHigh::combine).orElse(new LowHigh(0, 0));
                  writer.print(',');
                  writer.print(lohi.low);
                  writer.print(',');
                  writer.print(lohi.high);
               }

               writer.println();
            }
         }
         for (Map<String, Data> m : this.data.values()) {
            for (Data data : m.values()) {
               String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.metric) + "." + data.stepId + ".agent." + agent;
               persistHistogramAndSeries(filePrefix, data.perAgent.get(agent), data.agentSeries.get(agent));
            }
         }
      }
      try (PrintWriter writer = new PrintWriter(dir + File.separator + "failures.csv")) {
         writer.print("Phase,Metric,Message,Start,End,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println();
         for (SLA.Failure failure : failures) {
            writer.print(failure.phase());
            writer.print(',');
            writer.print(failure.metric());
            writer.print(",\"");
            writer.print(failure.message());
            writer.print("\",");
            StatisticsSummary summary = failure.statistics().summary(percentiles);
            writer.print(summary.startTime);
            writer.print(',');
            writer.print(summary.endTime);
            writer.print(',');
            summary.printTo(writer);
            writer.println();
         }
      }
      for (Map.Entry<String, SessionPoolStats> entry : sessionPoolStats.entrySet()) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + sanitize(entry.getKey()) + ".sessions.csv")) {
            SessionPoolStats sps = entry.getValue();
            writer.println("Timestamp,Address,MinSessions,MaxSessions");
            String[] addresses = new String[sps.records.size()];
            @SuppressWarnings("unchecked")
            Iterator<SessionPoolRecord>[] iterators = new Iterator[sps.records.size()];
            int counter = 0;
            for (Map.Entry<String, List<SessionPoolRecord>> byAddress : sps.records.entrySet()) {
               addresses[counter] = byAddress.getKey();
               iterators[counter] = byAddress.getValue().iterator();
               ++counter;
            }
            boolean hadNext;
            do {
               hadNext = false;
               for (int i = 0; i < addresses.length; ++i) {
                  if (iterators[i].hasNext()) {
                     SessionPoolRecord record = iterators[i].next();
                     writer.print(record.timestamp);
                     writer.print(',');
                     writer.print(addresses[i]);
                     writer.print(',');
                     writer.print(record.low);
                     writer.print(',');
                     writer.println(record.high);
                     hadNext = true;
                  }
               }
            } while (hadNext);
         }
      }
   }

   private String sanitize(String phase) {
      return phase.replaceAll(File.separator, "_");
   }

   private void persistHistogramAndSeries(String filePrefix, StatisticsSnapshot total, List<StatisticsSummary> series) throws FileNotFoundException {
      if (total != null) {
         try (PrintStream stream = new PrintStream(new FileOutputStream(filePrefix + ".histogram.csv"))) {
            total.histogram.outputPercentileDistribution(stream, 5, 1000_000.0, true);
         }
      }
      if (series != null) {
         try (PrintWriter writer = new PrintWriter(filePrefix + ".series.csv")) {
            writer.print("Start,End,");
            StatisticsSummary.printHeader(writer, percentiles);
            writer.println();
            for (StatisticsSummary summary : series) {
               writer.print(summary.startTime);
               writer.print(',');
               writer.print(summary.endTime);
               writer.print(',');
               summary.printTo(writer);
               writer.println();
            }
         }
      }
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

   public boolean validateSlas() {
      return failures.isEmpty();
   }

   public Map<String, Map<String, StatisticsSummary>> recentSummary(long minValidTimestamp) {
      Map<String, Map<String, StatisticsSummary>> result = new TreeMap<>();
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
            if (sum.requestCount == 0 || sum.histogram.getStartTimeStamp() < minValidTimestamp) {
               continue;
            }
            result.computeIfAbsent(data.phase, p -> new TreeMap<>()).put(data.metric, sum.summary(PERCENTILES));
         }
      }
      return result;
   }

   public Map<String, Map<String, StatisticsSummary>> totalSummary() {
      Map<String, Map<String, StatisticsSummary>> result = new TreeMap<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            StatisticsSummary last = data.total.summary(percentiles);
            result.computeIfAbsent(data.phase, p -> new TreeMap<>()).put(data.metric, last);
         }
      }
      return result;
   }

   public List<Client.CustomStats> customStats() {
      ArrayList<Client.CustomStats> list = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            for (Map.Entry<Object, CustomValue> entry : data.total.custom.entrySet()) {
               list.add(new Client.CustomStats(data.phase, data.stepId, data.metric, entry.getKey().toString(), entry.getValue().toString()));
            }
         }
      }
      Comparator<Client.CustomStats> c = Comparator.comparing(s -> s.phase);
      c = c.thenComparing(s -> s.stepId).thenComparing(s -> s.metric).thenComparing(s -> s.customName);
      Collections.sort(list, c);
      return list;
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

   private static final class Window {
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

   private final class Data {
      private final String phase;
      private final int stepId;
      private final String metric;
      // for reporting
      private final StatisticsSnapshot total = new StatisticsSnapshot();
      private final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
      private final Map<String, IntObjectMap<StatisticsSnapshot>> lastStats = new HashMap<>();
      private final List<StatisticsSummary> series = new ArrayList<>();
      private final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
      // floating statistics for SLAs
      private final Map<SLA, Window> windowSlas;
      private final SLA[] totalSlas;
      private int highestSequenceId = 0;

      private Data(String phase, int stepId, String metric, Map<SLA, Window> periodSlas, SLA[] totalSlas) {
         this.phase = phase;
         this.stepId = stepId;
         this.metric = metric;
         this.windowSlas = periodSlas;
         this.totalSlas = totalSlas;
      }

      private void record(String address, StatisticsSnapshot stats) {
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
               agentSeries.computeIfAbsent(entry.getKey(), a -> new ArrayList<>()).add(snapshot.summary(percentiles));
            }
         }
         if (sum.requestCount > 0) {
            series.add(sum.summary(percentiles));
         }
         for (Map.Entry<SLA, Window> entry : windowSlas.entrySet()) {
            SLA sla = entry.getKey();
            Window window = entry.getValue();

            window.add(sum);

            // If we haven't filled full window the SLA won't be validated
            SLA.Failure failure = sla.validate(phase, metric, window.current());
            if (window.isFull() && failure != null) {
               if (failures.size() < maxFailures) {
                  failures.add(failure);
               }
               failureHandler.accept(failure);
            }
         }
      }

      void completePhase() {
         for (int i = Math.max(0, highestSequenceId - MERGE_DELAY); i <= highestSequenceId; ++i) {
            mergeSnapshots(i);
         }
         for (SLA sla : totalSlas) {
            SLA.Failure failure = sla.validate(phase, metric, total);
            if (failure != null) {
               failures.add(failure);
               failureHandler.accept(failure);
            }
         }
      }
   }

   private static class SessionPoolStats {
      Map<String, List<SessionPoolRecord>> records = new HashMap<>();

      LowHigh findMinMax() {
         int min = Integer.MAX_VALUE;
         int max = 0;
         List<Iterator<SessionPoolRecord>> iterators = records.values().stream()
               .map(List::iterator).collect(Collectors.toList());
         for (;;) {
            LowHigh combined = iterators.stream()
                  .filter(Iterator::hasNext).map(Iterator::next).map(LowHigh.class::cast)
                  .reduce(LowHigh::sum).orElse(null);
            if (combined == null) break;
            min = Math.min(min, combined.low);
            max = Math.max(max, combined.high);
         }
         return new LowHigh(min, max);
      }
   }

   private static class SessionPoolRecord extends LowHigh {
      final long timestamp;

      private SessionPoolRecord(long timestamp, int min, int max) {
         super(min, max);
         this.timestamp = timestamp;
      }
   }
}
