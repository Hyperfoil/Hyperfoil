package io.hyperfoil.core.impl.statistics;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.client.Client;
import io.hyperfoil.core.util.LowHigh;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;

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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StatisticsStore {

   private static final String DEFAULT_FIELD_NAME = ":DEFAULT:";

   private static final double OUTPUT_VALUE_UNIT_SCALING_RATIO = 1000_000.0;
   private static final double[] PERCENTILES = new double[]{ 0.5, 0.9, 0.99, 0.999, 0.9999 };
   // When we receive snapshot with order #N we will attempt to compact agent snapshots #(N-60)
   // We are delaying this because the statistics for outlier may come with a significant delay
   private static final int MERGE_DELAY = 60;
   private static final Comparator<Client.RequestStats> REQUEST_STATS_COMPARATOR =
         Comparator.<Client.RequestStats, Long>comparing(rs -> rs.summary.startTime)
               .thenComparing(rs -> rs.phase).thenComparing(rs -> rs.metric);

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
               if (s1 != s2) {
                  throw new IllegalStateException();
               }
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

   public String toPhaseName(String phaseName, String iteration, String fork) {
      String rtrn = phaseName;
      if (iteration != null && !iteration.isEmpty() && !DEFAULT_FIELD_NAME.equals(iteration)) {
         rtrn = rtrn + "/" + iteration;
      }
      if (fork != null && !fork.isEmpty() && !DEFAULT_FIELD_NAME.equals(fork)) {
         rtrn = rtrn + "/" + fork;
      }
      return rtrn;
   }

   public String[] parsePhaseName(String phase) {
      String[] rtrn = new String[3];
      if (phase.contains("/")) {
         rtrn[0] = phase.substring(0, phase.indexOf("/"));
         phase = phase.substring(phase.indexOf("/") + 1);
      } else {
         rtrn[0] = phase;
         phase = "";
      }
      if (phase.isEmpty()) {
         rtrn[1] = DEFAULT_FIELD_NAME;
         rtrn[2] = DEFAULT_FIELD_NAME;
         return rtrn;
      }

      if (phase.contains("/")) {
         rtrn[1] = phase.substring(0, phase.indexOf("/"));
         phase = phase.substring(phase.indexOf("/") + 1);
         if (phase.isEmpty()) {
            phase = DEFAULT_FIELD_NAME;
         }
         rtrn[2] = phase;
         return rtrn;
      } else {
         //TODO determine if it is an iteration or fork
         if (phase.matches("[0-9]+")) {
            rtrn[1] = phase;
            rtrn[2] = DEFAULT_FIELD_NAME;
         } else {
            rtrn[1] = DEFAULT_FIELD_NAME;
            rtrn[2] = phase;
         }
         return rtrn;
      }
   }


   public void histogramArray(JsonGenerator jGenerator, Histogram histogram, double outputValueUnitScalingRatio) throws IOException {
      jGenerator.writeStartArray(); //start histogram
      AbstractHistogram.Percentiles iterationValues = histogram.percentiles(5);
      for (Iterator<HistogramIterationValue> iter = iterationValues.iterator(); iter.hasNext(); ) {
         HistogramIterationValue iterValue = iter.next();

         jGenerator.writeStartObject();
         jGenerator.writeNumberField("value", iterValue.getValueIteratedTo() / outputValueUnitScalingRatio);
         jGenerator.writeNumberField("percentile", iterValue.getPercentileLevelIteratedTo() / 100.0D);
         jGenerator.writeNumberField("totalCount", iterValue.getTotalCountToThisValue());
         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray(); //end histogram
   }

   public void seriesArray(JsonGenerator jGenerator, List<StatisticsSummary> series) throws IOException {
      jGenerator.writeStartArray(); //series
      if (series != null) {
         for (StatisticsSummary summary : series) {
            jGenerator.writeObject(summary);
         }
      }
      jGenerator.writeEndArray(); //end series
      jGenerator.flush();
   }

   public void totalArray(JsonGenerator jGenerator, Data[] dataList, Function<Data, StatisticsSummary> getSummary, BiConsumer<JsonGenerator, Data> also) throws IOException {
      jGenerator.writeStartArray();
      for (Data data : dataList) {
         StatisticsSummary summary = getSummary.apply(data); //data.total.summary(percentiles);
         if (summary == null) {
            continue;
         }
         jGenerator.writeStartObject();
         jGenerator.writeStringField("phase", data.phase);
         jGenerator.writeStringField("metric", data.metric);
         jGenerator.writeNumberField("start", data.total.histogram.getStartTimeStamp());
         jGenerator.writeNumberField("end", data.total.histogram.getEndTimeStamp());
         jGenerator.writeObjectField("summary", summary);

         if (also != null) {
            also.accept(jGenerator, data);
         }

         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray(); //end total.csv
   }

   private interface PhaseIterForkWalker<T> {
      default void onNewPhase(String phaseName) {
      }

      default void onEndPhase(String phaseName) {
      }

      default void onNewIter(String phaseName, String iterName) {
      }

      default void onEndIter(String phaseName, String iterName) {
      }

      default void onNewFork(String phaseName, String iterName, String forkName) {
      }

      default void onEndFork(String phaseName, String iterName, String forkName) {
      }

      void accept(String key, T data);
   }

   private <T> void walkPhaseIterFork(JsonGenerator jGenerator,
                                      T[] sorted,
                                      Function<T, String> toString,
                                      PhaseIterForkWalker<T> walker
   ) throws IOException {
      String phaseName = "";
      String iteration = "";
      String fork = "";
      jGenerator.writeStartObject();
      for (T data : sorted) {
         String phaseIterFork = toString.apply(data);
         String[] phaseSplit = parsePhaseName(phaseIterFork);
         String newPhase = phaseSplit[0];
         String newIter = phaseSplit[1];
         String newFork = phaseSplit[2];
         if (!phaseName.isEmpty()) {
            if (!fork.equals(newFork) || !iteration.equals(newIter) || !phaseName.equals(newPhase)) { //close previous fork
               walker.onEndFork(phaseName, iteration, fork);
               jGenerator.writeEndObject(); //end current fork
            }
            if (!iteration.equals(newIter) || !phaseName.equals(newPhase)) { //close previous iteration
               walker.onEndIter(phaseName, iteration);
               jGenerator.writeEndObject(); //end all forks
               jGenerator.writeEndObject(); //end current iter
            }
            if (!phaseName.equals(newPhase)) { //close previous phase
               walker.onEndPhase(phaseName);
               jGenerator.writeEndObject(); //end all iterations
               jGenerator.writeEndObject(); //end current phase
            }
         }
         if (!phaseName.equals(newPhase)) { //start of new phase
            jGenerator.writeFieldName(newPhase);
            phaseName = newPhase;
            jGenerator.writeStartObject();
            walker.onNewPhase(phaseName);
            jGenerator.writeFieldName("iteration");
            jGenerator.writeStartObject();
            iteration = ""; // guarantees iteration will be changed
         }
         if (!iteration.equals(newIter)) {
            jGenerator.writeFieldName(newIter);
            iteration = newIter;
            jGenerator.writeStartObject();
            walker.onNewIter(phaseName, iteration);
            jGenerator.writeFieldName("fork");
            jGenerator.writeStartObject();
            fork = "";
         }
         if (!fork.equals(newFork)) { //fork name should always be new
            jGenerator.writeFieldName(newFork);
            fork = newFork;
            jGenerator.writeStartObject();
            walker.onNewFork(phaseName, iteration, fork);
         }
         walker.accept(phaseIterFork, data);
      }
      walker.onEndFork(phaseName, iteration, fork);
      jGenerator.writeEndObject(); //close fork
      jGenerator.writeEndObject(); //close all forks
      walker.onEndIter(phaseName, iteration);
      jGenerator.writeEndObject(); //close iteration
      jGenerator.writeEndObject(); //close all iteration
      walker.onEndPhase(phaseName);
      jGenerator.writeEndObject(); //close phase
      jGenerator.writeEndObject(); //close all phases
   }

   public void writeJson(JsonGenerator jGenerator) throws IOException {
      Data[] sorted = this.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data d) -> d.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      jGenerator.writeStartObject();
      jGenerator.writeFieldName("total");
      totalArray(jGenerator, sorted, (data) -> data.total.summary(percentiles), null);

      jGenerator.writeFieldName("failure");
      jGenerator.writeStartArray();
      for (SLA.Failure failure : failures) {
         jGenerator.writeStartObject();
         jGenerator.writeStringField("phase", failure.phase());
         jGenerator.writeStringField("metric", failure.metric());
         jGenerator.writeStringField("message", failure.message());

         StatisticsSummary summary = failure.statistics().summary(percentiles);
         jGenerator.writeNumberField("start", summary.startTime);
         jGenerator.writeNumberField("end", summary.endTime);
         jGenerator.writeObjectField("percentileResponseTime", summary.percentileResponseTime);
         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray(); //end failure array

      if (sorted.length > 0) {
         //per phase.metric histogram and series
         jGenerator.writeFieldName("phase");
         walkPhaseIterFork(jGenerator, sorted, (data) -> data.phase,
               new PhaseIterForkWalker<Data>() {

                  @Override
                  public void onNewFork(String phaseName, String iterName, String forkName) {
                     try {
                        jGenerator.writeFieldName("metric");
                        jGenerator.writeStartObject();


                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }
                  }

                  @Override
                  public void onEndFork(String phaseName, String iterName, String forkName) {
                     String fullName = toPhaseName(phaseName, iterName, forkName);
                     try {
                        jGenerator.writeEndObject(); //end all metrics
                        jGenerator.flush();
                        if (sessionPoolStats.containsKey(fullName)) { //there are session data for the fully qualified forkName
                           SessionPoolStats sps = sessionPoolStats.get(fullName);
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
                           jGenerator.writeFieldName("sessions");
                           jGenerator.writeStartArray();
                           do {
                              hadNext = false;
                              for (int i = 0; i < addresses.length; ++i) {
                                 if (iterators[i].hasNext()) {
                                    SessionPoolRecord record = iterators[i].next();
                                    jGenerator.writeStartObject();
                                    jGenerator.writeNumberField("timestamp", record.timestamp);
                                    jGenerator.writeStringField("address", addresses[i]);
                                    jGenerator.writeNumberField("minSessions", record.low);
                                    jGenerator.writeNumberField("maxSessions", record.high);
                                    jGenerator.writeEndObject();
                                    hadNext = true;
                                 }
                              }
                           } while (hadNext);
                           jGenerator.writeEndArray(); //sessions array

                        }
                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }
                  }

                  @Override
                  public void accept(String key, Data data) {
                     try {
                        jGenerator.writeFieldName(data.metric);
                        jGenerator.writeStartObject(); //start metric

                        jGenerator.writeFieldName("histogram");

                        histogramArray(jGenerator, data.total.histogram, OUTPUT_VALUE_UNIT_SCALING_RATIO);
                        jGenerator.writeFieldName("series");
                        seriesArray(jGenerator, data.series);

                        jGenerator.writeEndObject(); //end metric
                        jGenerator.flush();

                     } catch (IOException e) {
                        throw new RuntimeException(e);
                     }
                  }
               });
      }
      String[] agents = this.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);

      jGenerator.writeFieldName("agent");
      jGenerator.writeStartObject();
      for (String agent : agents) {
         jGenerator.writeFieldName(agent);
         jGenerator.writeStartObject();

         jGenerator.writeFieldName("total");
         totalArray(jGenerator, sorted, (data) -> data.perAgent.get(agent).summary(percentiles), (jsonGenerator, data) -> {
            SessionPoolStats sps = sessionPoolStats.get(data.phase);
            if (sps != null && sps.records.get(agent) != null) {
               LowHigh lohi = sps.records.get(agent).stream().map(LowHigh.class::cast)
                     .reduce(LowHigh::combine).orElse(new LowHigh(0, 0));
               try {
                  jsonGenerator.writeNumberField("minSessions", lohi.low);
                  jsonGenerator.writeNumberField("maxSessions", lohi.high);
               } catch (IOException e) {
                  throw new RuntimeException(e);
               }
            }
         });
         if (sorted.length > 0) {
            jGenerator.writeFieldName("phase");
            walkPhaseIterFork(jGenerator, sorted, data -> data.phase,
                  new PhaseIterForkWalker<Data>() {
                     @Override
                     public void onNewFork(String phaseName, String iterName, String forkName) {
                        try {
                           jGenerator.writeFieldName("metric");
                           jGenerator.writeStartObject();
                        } catch (IOException e) {
                           throw new RuntimeException(e);
                        }
                     }

                     @Override
                     public void onEndFork(String phaseName, String iterName, String forkName) {
                        try {
                           jGenerator.writeEndObject();
                           if (sessionPoolStats.containsKey(forkName)) {
                              SessionPoolStats sps = sessionPoolStats.get(forkName);
                              if (sps.records.containsKey(agent)) {
                                 List<SessionPoolRecord> records = sps.records.get(agent);
                                 jGenerator.writeFieldName("sessions");
                                 jGenerator.writeStartArray();
                                 for (SessionPoolRecord record : records) {
                                    jGenerator.writeStartObject();
                                    jGenerator.writeNumberField("timestamp", record.timestamp);
                                    jGenerator.writeNumberField("minSessions", record.low);
                                    jGenerator.writeNumberField("maxSessions", record.high);
                                    jGenerator.writeEndObject();
                                 }
                                 jGenerator.writeEndArray();
                              }
                           }
                        } catch (IOException e) {
                           throw new RuntimeException(e);
                        }
                     }

                     @Override
                     public void accept(String key, Data data) {
                        try {
                           jGenerator.writeFieldName(data.metric);
                           jGenerator.writeStartObject();
                           jGenerator.writeFieldName("histogram");
                           histogramArray(jGenerator, data.perAgent.get(agent).histogram, OUTPUT_VALUE_UNIT_SCALING_RATIO);
                           jGenerator.writeFieldName("series");
                           seriesArray(jGenerator, data.agentSeries.get(agent));

                           jGenerator.writeEndObject();
                        } catch (IOException e) {
                           throw new RuntimeException(e);
                        }
                     }
                  });
         }
         jGenerator.writeEndObject(); //each agent
      }
      jGenerator.writeEndObject(); //all agents
      jGenerator.writeEndObject();
      return;
   }

   public void persist(Path dir) throws IOException {
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      Data[] sorted = this.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data d) -> d.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      try (FileOutputStream stream = new FileOutputStream(dir + File.separator + "all.json")) {
         JsonFactory jfactory = new JsonFactory();
         jfactory.setCodec(new ObjectMapper());
         JsonGenerator jGenerator = jfactory.createGenerator(stream, JsonEncoding.UTF8);
         jGenerator.setCodec(new ObjectMapper());

         writeJson(jGenerator);
         jGenerator.close();
      }

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

   public List<Client.RequestStats> recentSummary(long minValidTimestamp) {
      ArrayList<Client.RequestStats> result = new ArrayList<>();
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
                  .filter(f -> f.phase().equals(data.phase) && f.metric().equals(data.metric))
                  .map(f -> f.message()).collect(Collectors.toList());
            result.add(new Client.RequestStats(data.phase, data.metric, sum.summary(PERCENTILES), failures));
         }
      }
      result.sort(REQUEST_STATS_COMPARATOR);
      return result;
   }

   public List<Client.RequestStats> totalSummary() {
      ArrayList<Client.RequestStats> result = new ArrayList<>();
      for (Map<String, Data> m : this.data.values()) {
         for (Data data : m.values()) {
            StatisticsSummary last = data.total.summary(percentiles);
            List<String> failures = this.failures.stream()
                  .filter(f -> f.phase().equals(data.phase) && f.metric().equals(data.metric))
                  .map(f -> f.message()).collect(Collectors.toList());
            result.add(new Client.RequestStats(data.phase, data.metric, last, failures));
         }
      }
      result.sort(REQUEST_STATS_COMPARATOR);
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
         if (!sum.isEmpty()) {
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

   private static class SessionPoolRecord extends LowHigh {
      final long timestamp;

      private SessionPoolRecord(long timestamp, int min, int max) {
         super(min, max);
         this.timestamp = timestamp;
      }
   }
}
