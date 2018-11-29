package io.sailrocket.core.impl.statistics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.SLA;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.statistics.StatisticsSnapshot;
import io.sailrocket.api.statistics.StatisticsSummary;

public class StatisticsStore {
   private final Benchmark benchmark;
   private final int numAgents;
   private final Map<PhaseSeq, Data> data = new HashMap<>();
   private final Consumer<SLA.Failure> failureHandler;
   private final double[] percentiles;
   private final List<SLA.Failure> failures = new ArrayList<>();
   private final int maxFailures = 100;


   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler, double[] percentiles) {
      this.benchmark = benchmark;
      this.numAgents = Math.max(benchmark.agents().length, 1);
      this.failureHandler = failureHandler;
      this.percentiles = percentiles;
      long collectionPeriod = benchmark.simulation().statisticsCollectionPeriod();
      for (Phase phase : benchmark.simulation().phases()) {
         for (Sequence sequence : phase.scenario().sequences()) {
            Map<SLA, Window> rings = Stream.of(benchmark.slas())
                  .filter(sla -> sla.sequence() == sequence && sla.window() > 0)
                  .collect(Collectors.toMap(Function.identity(), sla -> new Window((int) (sla.window() / collectionPeriod))));
            SLA[] total = Stream.of(benchmark.slas())
                  .filter(sla -> sla.sequence() == sequence && sla.window() <= 0).toArray(SLA[]::new);
            data.put(new PhaseSeq(phase.name, sequence.name()), new Data(rings, total));
         }
      }
   }

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler) {
      this(benchmark, failureHandler, new double[] { 0.5, 0.9, 0.99, 0.999, 0.9999 });
   }

   public void record(String address, String phase, String sequence, StatisticsSnapshot stats) {
      Data data = this.data.get(new PhaseSeq(phase, sequence));
      data.record(address, stats);
   }

   public void persist(Path dir) throws IOException {
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      PhaseSeq[] phaseSeqs = data.keySet().toArray(new PhaseSeq[0]);
      Arrays.sort(phaseSeqs);

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Sequence,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println();
         for (PhaseSeq phaseSeq : phaseSeqs) {
            writer.print(phaseSeq.phase);
            writer.print(',');
            writer.print(phaseSeq.sequence);
            writer.print(',');
            data.get(phaseSeq).total.summary(percentiles).printTo(writer);
            writer.println();
         }
      }
      for (Map.Entry<PhaseSeq, Data> entry : data.entrySet()) {
         String filePrefix = dir + File.separator + sanitize(entry.getKey().phase) + "." + sanitize(entry.getKey().sequence);
         persistHistogramAndSeries(filePrefix, entry.getValue().total, entry.getValue().series);
      }
      String[] agents = data.values().stream().flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Sequence,");
            StatisticsSummary.printHeader(writer, percentiles);
            writer.println();
            for (PhaseSeq phaseSeq : phaseSeqs) {
               StatisticsSnapshot agentStats = data.get(phaseSeq).perAgent.get(agent);
               if (agentStats == null) {
                  continue;
               }
               writer.print(phaseSeq.phase);
               writer.print(',');
               writer.print(phaseSeq.sequence);
               writer.print(',');
               agentStats.summary(percentiles).printTo(writer);
               writer.println();
            }
         }
         for (Map.Entry<PhaseSeq, Data> entry : data.entrySet()) {
            String filePrefix = dir + File.separator + sanitize(entry.getKey().phase) + "." + sanitize(entry.getKey().sequence) + ".agent." + agent;
            persistHistogramAndSeries(filePrefix, entry.getValue().perAgent.get(agent), entry.getValue().agentSeries.get(agent));
         }
      }
      try (PrintWriter writer = new PrintWriter(dir + File.separator + "failures.csv")) {
         writer.print("Phase,Sequence,Message,Start,End,");
         StatisticsSummary.printHeader(writer, percentiles);
         writer.println();
         for (SLA.Failure failure : failures) {
            writer.print(failure.sla().sequence().phase());
            writer.print(',');
            writer.print(failure.sla().sequence().name());
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
      for (Map.Entry<PhaseSeq, Data> entry : data.entrySet()) {
         if (entry.getKey().phase.equals(phase)) {
            entry.getValue().validateTotalSlas();
         }
      }
      return failures.isEmpty();
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
      // for reporting
      private final StatisticsSnapshot total = new StatisticsSnapshot();
      private final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
      private final Map<String, List<StatisticsSnapshot>> lastStats = new HashMap<>();
      private final List<StatisticsSummary> series = new ArrayList<>();
      private final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
      // floating statistics for SLAs
      private final Map<SLA, Window> windowSlas;
      private final SLA[] totalSlas;

      private Data(Map<SLA, Window> periodSlas, SLA[] totalSlas) {
         this.windowSlas = periodSlas;
         this.totalSlas = totalSlas;
      }

      public void record(String address, StatisticsSnapshot stats) {
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
               SLA.Failure failure = sla.validate(window.current());
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
            SLA.Failure failure = sla.validate(total);
            if (failure != null) {
               failures.add(failure);
               failureHandler.accept(failure);
               continue;
            }
         }
      }
   }

   private static final class PhaseSeq implements Comparable<PhaseSeq> {
      private final String phase;
      private final String sequence;

      private PhaseSeq(String phase, String sequence) {
         this.phase = phase;
         this.sequence = sequence;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         PhaseSeq phaseSeq = (PhaseSeq) o;
         return Objects.equals(phase, phaseSeq.phase) &&
               Objects.equals(sequence, phaseSeq.sequence);
      }

      @Override
      public int hashCode() {

         return Objects.hash(phase, sequence);
      }

      @Override
      public int compareTo(PhaseSeq o) {
         int pc = phase.compareTo(o.phase);
         return pc != 0 ? pc : sequence.compareTo(o.sequence);
      }
   }
}
