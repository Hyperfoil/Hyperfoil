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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;

public class StatisticsStore {
   public static final double[] PERCENTILES = new double[]{0.5, 0.9, 0.99, 0.999, 0.9999};

   private final Benchmark benchmark;
   private final int numAgents;
   private final Map<Integer, Data> data = new HashMap<>();
   private final Consumer<SLA.Failure> failureHandler;
   private final double[] percentiles;
   private final List<SLA.Failure> failures = new ArrayList<>();
   private final int maxFailures = 100;
   private final Map<Integer, SLA.Provider> slaProviders;

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler, double[] percentiles) {
      this.benchmark = benchmark;
      this.numAgents = Math.max(benchmark.agents().length, 1);
      this.failureHandler = failureHandler;
      this.percentiles = percentiles;
      this.slaProviders = benchmark.steps()
            .filter(SLA.Provider.class::isInstance).map(SLA.Provider.class::cast)
            .collect(Collectors.toMap(SLA.Provider::id, Function.identity()));
   }

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler) {
      this(benchmark, failureHandler, PERCENTILES);
   }

   public void record(String address, int stepId, String statisticsName, StatisticsSnapshot stats) {
      Data data = this.data.get(stepId);
      if (data == null) {
         long collectionPeriod = benchmark.simulation().statisticsCollectionPeriod();
         SLA.Provider slaProvider = slaProviders.get(stepId);
         Map<SLA, Window> rings = slaProvider == null ? Collections.emptyMap() :
               Stream.of(slaProvider.sla()).filter(sla -> sla.window() > 0).collect(
                  Collectors.toMap(Function.identity(),
                  sla -> new Window((int) (sla.window() / collectionPeriod))));
         SLA[] total = slaProvider == null ? new SLA[0] : Stream.of(slaProvider.sla())
               .filter(sla -> sla.window() <= 0).toArray(SLA[]::new);
         String phase = slaProvider.sequence().phase().name();
         this.data.put(stepId, new Data(phase, stepId, statisticsName, rings, total));
      }
      data.record(address, stats);
   }

   public void persist(Path dir) throws IOException {
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      Data[] sorted = this.data.values().toArray(new Data[0]);
      Arrays.sort(sorted, (d1, d2) -> {
         int cmp = d1.phase.compareTo(d2.phase);
         if (cmp != 0) return cmp;
         cmp = d1.statisticsName.compareTo(d2.statisticsName);
         if (cmp != 0) return cmp;
         return Integer.compare(d1.stepId, d2.stepId);
      });

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Name,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println();
         for (Data data : sorted) {
            writer.print(data.phase);
            writer.print(',');
            writer.print(data.statisticsName);
            writer.print(',');
            data.total.summary(percentiles).printTo(writer);
            writer.println();
         }
      }
      for (Data data: this.data.values()) {
         String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.statisticsName) + "." + data.stepId;
         persistHistogramAndSeries(filePrefix, data.total, data.series);
      }
      String[] agents = this.data.values().stream().flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Name,");
            StatisticsSummary.printHeader(writer, percentiles);
            writer.println();
            for (Data data : sorted) {
               StatisticsSnapshot agentStats = data.perAgent.get(agent);
               if (agentStats == null) {
                  continue;
               }
               writer.print(data.phase);
               writer.print(',');
               writer.print(data.statisticsName);
               writer.print(',');
               agentStats.summary(percentiles).printTo(writer);
               writer.println();
            }
         }
         for (Data data : this.data.values()) {
            String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.statisticsName) + "." + data.stepId + ".agent." + agent;
            persistHistogramAndSeries(filePrefix, data.perAgent.get(agent), data.agentSeries.get(agent));
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
      for (Data data : data.values()) {
         if (data.phase.equals(phase)) {
            data.validateTotalSlas();
         }
      }
      return failures.isEmpty();
   }

   public Map<String, Map<String, StatisticsSummary>> recentSummary(long minValidTimestamp) {
      Map<String, Map<String, StatisticsSummary>> result = new HashMap<>();
      for (Data data : data.values()) {
         List<StatisticsSummary> series = data.series;
         if (series.isEmpty()) {
            continue;
         }
         StatisticsSummary last = series.get(series.size() - 1);
         if (last.startTime < minValidTimestamp) {
            continue;
         }
         result.computeIfAbsent(data.phase, p -> new HashMap<>()).put(data.statisticsName, last);
      }
      return result;
   }

   public Map<String, Map<String, StatisticsSummary>> totalSummary() {
      Map<String, Map<String, StatisticsSummary>> result = new HashMap<>();
      for (Data data : data.values()) {
         StatisticsSummary last = data.total.summary(percentiles);
         result.computeIfAbsent(data.phase, p -> new HashMap<>()).put(data.statisticsName, last);
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
}
