package io.sailrocket.core.impl.statistics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.Phase;
import io.sailrocket.api.SLA;
import io.sailrocket.api.Sequence;
import io.sailrocket.api.StatisticsSnapshot;
import io.sailrocket.api.statistics.StatisticsSummary;

public class StatisticsStore {
   private final Benchmark benchmark;
   private final Map<PhaseSeq, Data> data = new HashMap<>();
   private final Consumer<SLA.Failure> failureHandler;
   private final double[] percentiles;

   public StatisticsStore(Benchmark benchmark, Consumer<SLA.Failure> failureHandler, double[] percentiles) {
      this.benchmark = benchmark;
      this.failureHandler = failureHandler;
      this.percentiles = percentiles;
      long collectionPeriod = benchmark.simulation().statisticsCollectionPeriod();
      for (Phase phase : benchmark.simulation().phases()) {
         for (Sequence sequence : phase.scenario().sequences()) {
            Map<SLA, Window> rings = Stream.of(benchmark.slas())
                  .filter(sla -> sla.sequence() == sequence && sla.window() > 0)
                  .collect(Collectors.toMap(Function.identity(), sla -> new Window((int) (sla.window() / collectionPeriod))));
            SLA[] overall = Stream.of(benchmark.slas())
                  .filter(sla -> sla.sequence() == sequence && sla.window() <= 0).toArray(SLA[]::new);
            data.put(new PhaseSeq(phase.name, sequence.name()), new Data(rings, overall));
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

   public void benchmarkCompleted() {
      for (Data data : data.values()) {
         data.validateOverallSlas();
      }
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
      private final StatisticsSnapshot overall = new StatisticsSnapshot();
      private final Map<String, StatisticsSnapshot> perAgent = new HashMap<>();
      private final Map<String, List<StatisticsSnapshot>> lastStats = new HashMap<>();
      private final List<StatisticsSummary> series = new ArrayList<>();
      private final Map<String, List<StatisticsSummary>> agentSeries = new HashMap<>();
      // floating statistics for SLAs
      private final Map<SLA, Window> windowSlas;
      private final SLA[] overallSlas;

      private Data(Map<SLA, Window> periodSlas, SLA[] overallSlas) {
         this.windowSlas = periodSlas;
         this.overallSlas = overallSlas;
      }

      public void record(String address, StatisticsSnapshot stats) {
         stats.addInto(overall);
         stats.addInto(perAgent.computeIfAbsent(address, a -> new StatisticsSnapshot()));
         lastStats.computeIfAbsent(address, a -> new LinkedList<>()).add(stats);
         if (lastStats.values().stream().filter(l -> !l.isEmpty()).count() == benchmark.agents().length) {
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
                  failureHandler.accept(failure);
               }
            }
         }
         agentSeries.computeIfAbsent(address, a -> new ArrayList<>()).add(stats.summary(percentiles));
      }

      public void validateOverallSlas() {
         for (SLA sla : overallSlas) {
            SLA.Failure failure = sla.validate(overall);
            if (failure != null) {
               failureHandler.accept(failure);
               continue;
            }
         }
      }
   }

   private static final class PhaseSeq {
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
   }
}
