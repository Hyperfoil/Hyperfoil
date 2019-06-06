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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

public class StatisticsStore {
   private static final double[] PERCENTILES = new double[]{0.5, 0.9, 0.99, 0.999, 0.9999};

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

   public void record(String address, int phaseId, int stepId, String statisticsName, StatisticsSnapshot stats) {
      Map<String, Data> map = this.data.computeIfAbsent((phaseId << 16) + stepId, phaseStep -> new HashMap<>());
      Data data = map.get(statisticsName);
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
         map.put(statisticsName, data = new Data(phase, stepId, statisticsName, rings, total));
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
         cmp = d1.statisticsName.compareTo(d2.statisticsName);
         if (cmp != 0) return cmp;
         return Integer.compare(d1.stepId, d2.stepId);
      });

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Name,Start,End,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println(",MinSessions,MaxSessions");
         for (Data data : sorted) {
            writer.print(data.phase);
            writer.print(',');
            writer.print(data.statisticsName);
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
            String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.statisticsName) + "." + data.stepId;
            persistHistogramAndSeries(filePrefix, data.total, data.series);
         }
      }
      String[] agents = this.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Name,Start,End,");
            StatisticsSummary.printHeader(writer, percentiles);
            writer.println(",MinSessions,MaxSessions");
            for (Data data : sorted) {
               StatisticsSnapshot agentStats = data.perAgent.get(agent);
               if (agentStats == null) {
                  continue;
               }
               writer.print(data.phase);
               writer.print(',');
               writer.print(data.statisticsName);
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
               String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.statisticsName) + "." + data.stepId + ".agent." + agent;
               persistHistogramAndSeries(filePrefix, data.perAgent.get(agent), data.agentSeries.get(agent));
            }
         }
      }
      try (PrintWriter writer = new PrintWriter(dir + File.separator + "failures.csv")) {
         writer.print("Phase,Statistics,Message,Start,End,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println();
         for (SLA.Failure failure : failures) {
            writer.print(failure.phase());
            writer.print(',');
            writer.print(failure.statisticsName());
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

   public boolean validateSlas(String phase) {
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            if (data.phase.equals(phase)) {
               data.validateTotalSlas();
            }
         }
      }
      return failures.isEmpty();
   }

   public Map<String, Map<String, StatisticsSummary>> recentSummary(long minValidTimestamp) {
      Map<String, Map<String, StatisticsSummary>> result = new TreeMap<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            List<StatisticsSummary> series = data.series;
            if (series.isEmpty()) {
               continue;
            }
            StatisticsSummary last = series.get(series.size() - 1);
            if (last.startTime < minValidTimestamp) {
               continue;
            }
            result.computeIfAbsent(data.phase, p -> new TreeMap<>()).put(data.statisticsName, last);
         }
      }
      return result;
   }

   public Map<String, Map<String, StatisticsSummary>> totalSummary() {
      Map<String, Map<String, StatisticsSummary>> result = new TreeMap<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            StatisticsSummary last = data.total.summary(percentiles);
            result.computeIfAbsent(data.phase, p -> new TreeMap<>()).put(data.statisticsName, last);
         }
      }
      return result;
   }

   public List<Client.CustomStats> customStats() {
      ArrayList<Client.CustomStats> list = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            for (Map.Entry<Object, CustomValue> entry : data.total.custom.entrySet()) {
               list.add(new Client.CustomStats(data.phase, data.stepId, data.statisticsName, entry.getKey().toString(), entry.getValue().toString()));
            }
         }
      }
      Comparator<Client.CustomStats> c = Comparator.comparing(s -> s.phase);
      c = c.thenComparing(s -> s.stepId).thenComparing(s -> s.statsName).thenComparing(s -> s.customName);
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
      private final String statisticsName;
      // for reporting
      private final StatisticsSnapshot total = new StatisticsSnapshot();
      private final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
      private final Map<String, List<StatisticsSnapshot>> lastStats = new HashMap<>();
      private final List<StatisticsSummary> series = new ArrayList<>();
      private final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
      // floating statistics for SLAs
      private final Map<SLA, Window> windowSlas;
      private final SLA[] totalSlas;

      private Data(String phase, int stepId, String statisticsName, Map<SLA, Window> periodSlas, SLA[] totalSlas) {
         this.phase = phase;
         this.stepId = stepId;
         this.statisticsName = statisticsName;
         this.windowSlas = periodSlas;
         this.totalSlas = totalSlas;
      }

      private void record(String address, StatisticsSnapshot stats) {
         stats.addInto(total);
         stats.addInto(perAgent.computeIfAbsent(address, a -> new StatisticsSnapshot()));
         lastStats.computeIfAbsent(address, a -> new LinkedList<>()).add(stats);
         if (lastStats.values().stream().filter(l -> !l.isEmpty()).count() == numAgents) {
            StatisticsSnapshot sum = new StatisticsSnapshot();
            for (List<StatisticsSnapshot> list : lastStats.values()) {
               list.remove(0).addInto(sum);
            }
            series.add(sum.summary(percentiles));
            for (Map.Entry<SLA, Window> entry : windowSlas.entrySet()) {
               SLA sla = entry.getKey();
               Window window = entry.getValue();

               window.add(sum);

               // If we haven't filled full window the SLA won't be validated
               SLA.Failure failure = sla.validate(phase, statisticsName, window.current());
               if (window.isFull() && failure != null) {
                  if (failures.size() < maxFailures) {
                     failures.add(failure);
                  }
                  failureHandler.accept(failure);
               }
            }
         }
         agentSeries.computeIfAbsent(address, a -> new ArrayList<>()).add(stats.summary(percentiles));
      }

      public void validateTotalSlas() {
         for (SLA sla : totalSlas) {
            SLA.Failure failure = sla.validate(phase, statisticsName, total);
            if (failure != null) {
               failures.add(failure);
               failureHandler.accept(failure);
               continue;
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
