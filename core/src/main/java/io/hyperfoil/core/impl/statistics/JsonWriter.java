package io.hyperfoil.core.impl.statistics;

import com.fasterxml.jackson.core.JsonGenerator;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.util.LowHigh;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramIterationValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JsonWriter {
   private static final String DEFAULT_FIELD_NAME = ":DEFAULT:";

   public static void writeArrayJsons(StatisticsStore store, JsonGenerator jGenerator, Map<String, Object> props) throws IOException {
      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data data) -> data.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      jGenerator.writeStartObject(); //root of object

      if (props != null && !props.isEmpty()) {

         props.forEach((key, value) -> {
            try {
               if (value instanceof JsonObject) {
                  jGenerator.writeFieldName(key);
                  jGenerator.writeRawValue(((JsonObject) value).encode());
               } else if (value instanceof JsonArray) {
                  jGenerator.writeFieldName(key);
                  jGenerator.writeRawValue(((JsonArray) value).encode());
               } else if (value instanceof Long) {
                  jGenerator.writeNumberField(key, (Long) value);
               } else if (value instanceof Integer) {
                  jGenerator.writeNumberField(key, (Integer) value);
               } else if (value instanceof Double) {
                  jGenerator.writeNumberField(key, (Double) value);
               } else if (value instanceof Float) {
                  jGenerator.writeNumberField(key, (Float) value);
               } else if (value instanceof Boolean) {
                  jGenerator.writeBooleanField(key, (Boolean) value);
               } else {
                  jGenerator.writeStringField(key, value.toString());
               }
            } catch (IOException e) {
               e.printStackTrace();
            }
         });
      }

      jGenerator.writeFieldName("failures");
      jGenerator.writeStartArray();
      for (SLA.Failure failure : store.getFailures()) {
         jGenerator.writeStartObject();
         jGenerator.writeStringField("phase", failure.phase());
         jGenerator.writeStringField("metric", failure.metric());
         jGenerator.writeStringField("message", failure.message());

         StatisticsSummary summary = failure.statistics().summary(StatisticsStore.PERCENTILES);
         jGenerator.writeNumberField("start", summary.startTime);
         jGenerator.writeNumberField("end", summary.endTime);
         jGenerator.writeObjectField("percentileResponseTime", summary.percentileResponseTime);
         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray();


      jGenerator.writeFieldName("stats");
      jGenerator.writeStartArray(); //stats array
      for (Data data : sorted) {
         jGenerator.writeStartObject(); //entry

         jGenerator.writeStringField("name", data.phase);

         String[] split = parsePhaseName(data.phase, "");
         jGenerator.writeStringField("phase", split[0]);
         jGenerator.writeStringField("iteration", split[1]);
         jGenerator.writeStringField("fork", split[2]);
         jGenerator.writeStringField("metric", data.metric);

         jGenerator.writeFieldName("total");
         writeTotalValue(jGenerator, data, d -> d.total, store.sessionPoolStats.get(data.phase).findMinMax());

         jGenerator.writeFieldName("histogram");
         jGenerator.writeStartObject();
         jGenerator.writeFieldName("percentiles");
         histogramArray(jGenerator, data.total.histogram.percentiles(5).iterator());
         jGenerator.writeFieldName("linear");
         histogramArray(jGenerator, data.total.histogram.linearBucketValues(1_000_000).iterator());
         jGenerator.writeEndObject(); //histogram

         jGenerator.writeFieldName("series");
         seriesArray(jGenerator, data.series);

         jGenerator.writeEndObject(); //entry
      }
      jGenerator.writeEndArray(); //stats array


      jGenerator.writeFieldName("sessions");
      jGenerator.writeStartArray(); //phase sessions array
      for (Data data : sorted) {
         if (store.sessionPoolStats.containsKey(data.phase)) {
            jGenerator.writeStartObject(); //session entry
            jGenerator.writeStringField("name", data.phase);

            String[] split = parsePhaseName(data.phase, "");
            jGenerator.writeStringField("phase", split[0]);
            jGenerator.writeStringField("iteration", split[1]);
            jGenerator.writeStringField("fork", split[2]);

            StatisticsStore.SessionPoolStats sps = store.sessionPoolStats.get(data.phase);
            String[] addresses = new String[sps.records.size()];
            Iterator<StatisticsStore.SessionPoolRecord>[] iterators = new Iterator[sps.records.size()];
            int counter = 0;
            for (Map.Entry<String, List<StatisticsStore.SessionPoolRecord>> byAddress : sps.records.entrySet()) {
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
                     StatisticsStore.SessionPoolRecord record = iterators[i].next();
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
            jGenerator.writeEndObject(); //phase session entry
         }
      }
      jGenerator.writeEndArray();
      String[] agents = store.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      jGenerator.writeFieldName("agents");

      jGenerator.writeStartArray(); //agents array

      for (String agent : agents) {
         jGenerator.writeStartObject(); //agent
         jGenerator.writeStringField("name", agent);
         jGenerator.writeFieldName("stats");

         jGenerator.writeStartArray(); //agent stats array

         for (Data data : sorted) {
            if (data.perAgent.containsKey(agent)) {
               StatisticsSnapshot agentStats = data.perAgent.get(agent);
               jGenerator.writeStartObject(); // agent stats entry

               jGenerator.writeStringField("name", data.phase);

               String[] split = parsePhaseName(data.phase, "");
               jGenerator.writeStringField("phase", split[0]);
               jGenerator.writeStringField("iteration", split[1]);
               jGenerator.writeStringField("fork", split[2]);
               jGenerator.writeStringField("metric", data.metric);

               jGenerator.writeFieldName("total");
               writeTotalValue(
                     jGenerator,
                     data,
                     d -> d.perAgent.get(agent),
                     store.sessionPoolStats.getOrDefault(data.phase, new StatisticsStore.SessionPoolStats())
                           .records.getOrDefault(agent, new ArrayList<>())
                           .stream()
                           .map(LowHigh.class::cast)
                           .reduce(LowHigh::combine)
                           .orElse(new LowHigh(0, 0))
               );

               jGenerator.writeFieldName("histogram");
               jGenerator.writeStartObject(); // histograms

               jGenerator.writeFieldName("percentiles");
               histogramArray(jGenerator, data.perAgent.get(agent).histogram.percentiles(5).iterator());

               jGenerator.writeFieldName("linear");
               histogramArray(jGenerator, data.perAgent.get(agent).histogram.linearBucketValues(1_000_000).iterator());

               jGenerator.writeEndObject(); // histograms

               jGenerator.writeFieldName("series");
               seriesArray(jGenerator, data.agentSeries.get(agent));

               jGenerator.writeEndObject(); // agent stats entry
            }
         }
         jGenerator.writeEndArray(); //agent stats array
         jGenerator.writeEndObject(); //agent
      }

      jGenerator.writeEndArray(); //agents array

      jGenerator.writeEndObject(); //root of object
   }

   public static void writeJson(StatisticsStore store, JsonGenerator jGenerator, boolean standalone) throws IOException {
      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data data) -> data.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      if (standalone) {
         jGenerator.writeStartObject();
      }
      jGenerator.writeFieldName("total");
      totalArray(jGenerator, sorted, store.sessionPoolStats, d -> d.total, StatisticsStore.SessionPoolStats::findMinMax);

      jGenerator.writeFieldName("failure");
      jGenerator.writeStartArray();
      for (SLA.Failure failure : store.failures) {
         jGenerator.writeStartObject();
         jGenerator.writeStringField("phase", failure.phase());
         jGenerator.writeStringField("metric", failure.metric());
         jGenerator.writeStringField("message", failure.message());

         StatisticsSummary summary = failure.statistics().summary(StatisticsStore.PERCENTILES);
         jGenerator.writeNumberField("start", summary.startTime);
         jGenerator.writeNumberField("end", summary.endTime);
         jGenerator.writeObjectField("percentileResponseTime", summary.percentileResponseTime);
         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray(); //end failure array

      if (sorted.length > 0) {
         //per phase.metric histogram and series
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
                     String fullName = toPhaseName(phaseName, iterName, forkName);
                     try {
                        jGenerator.writeEndObject(); //end all metrics
                        jGenerator.flush();
                        if (store.sessionPoolStats.containsKey(fullName)) { //there are session data for the fully qualified forkName
                           StatisticsStore.SessionPoolStats sps = store.sessionPoolStats.get(fullName);
                           String[] addresses = new String[sps.records.size()];
                           @SuppressWarnings("unchecked")
                           Iterator<StatisticsStore.SessionPoolRecord>[] iterators = new Iterator[sps.records.size()];
                           int counter = 0;
                           for (Map.Entry<String, List<StatisticsStore.SessionPoolRecord>> byAddress : sps.records.entrySet()) {
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
                                    StatisticsStore.SessionPoolRecord record = iterators[i].next();
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
                        jGenerator.writeStartObject();
                        //histogramArray(jGenerator, data.total.histogram, OUTPUT_VALUE_UNIT_SCALING_RATIO);
                        jGenerator.writeFieldName("percentiles");
                        histogramArray(jGenerator, data.total.histogram.percentiles(5).iterator());
                        jGenerator.writeFieldName("linear");
                        histogramArray(jGenerator, data.total.histogram.linearBucketValues(1_000_000).iterator());
                        jGenerator.writeEndObject(); //histogram
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
      String[] agents = store.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);

      jGenerator.writeFieldName("agent");
      jGenerator.writeStartObject();
      for (String agent : agents) {
         jGenerator.writeFieldName(agent);
         jGenerator.writeStartObject();

         jGenerator.writeFieldName("total");
         totalArray(jGenerator, sorted, store.sessionPoolStats, data -> data.perAgent.get(agent), sps -> {
            if (sps.records.get(agent) != null) {
               return sps.records.get(agent).stream().map(LowHigh.class::cast)
                     .reduce(LowHigh::combine).orElse(new LowHigh(0, 0));
            } else {
               return new LowHigh(0, 0);
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
                           if (store.sessionPoolStats.containsKey(forkName)) {
                              StatisticsStore.SessionPoolStats sps = store.sessionPoolStats.get(forkName);
                              if (sps.records.containsKey(agent)) {
                                 List<StatisticsStore.SessionPoolRecord> records = sps.records.get(agent);
                                 jGenerator.writeFieldName("sessions");
                                 jGenerator.writeStartArray();
                                 for (StatisticsStore.SessionPoolRecord record : records) {
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

                           Histogram histogram = data.perAgent.get(agent).histogram;
                           jGenerator.writeFieldName("histogram");
                           jGenerator.writeStartObject();
                           jGenerator.writeFieldName("percentiles");
                           histogramArray(jGenerator, histogram.percentiles(5).iterator());
                           jGenerator.writeFieldName("linear");
                           histogramArray(jGenerator, histogram.linearBucketValues(1_000_000).iterator());
                           jGenerator.writeEndObject(); //histogram
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
      if (standalone) {
         jGenerator.writeEndObject();
      }
   }

   private static String toPhaseName(String phaseName, String iteration, String fork) {
      String rtrn = phaseName;
      if (iteration != null && !iteration.isEmpty() && !DEFAULT_FIELD_NAME.equals(iteration)) {
         rtrn = rtrn + "/" + iteration;
      }
      if (fork != null && !fork.isEmpty() && !DEFAULT_FIELD_NAME.equals(fork)) {
         rtrn = rtrn + "/" + fork;
      }
      return rtrn;
   }

   private static String[] parsePhaseName(String phase) {
      return parsePhaseName(phase, DEFAULT_FIELD_NAME);
   }

   private static String[] parsePhaseName(String phase, String defaultName) {
      String[] rtrn = new String[3];
      if (phase.contains("/")) {
         rtrn[0] = phase.substring(0, phase.indexOf("/"));
         phase = phase.substring(phase.indexOf("/") + 1);
      } else {
         rtrn[0] = phase;
         phase = "";
      }
      if (phase.isEmpty()) {
         rtrn[1] = defaultName;
         rtrn[2] = defaultName;
         return rtrn;
      }

      if (phase.contains("/")) {
         rtrn[1] = phase.substring(0, phase.indexOf("/"));
         phase = phase.substring(phase.indexOf("/") + 1);
         if (phase.isEmpty()) {
            phase = defaultName;
         }
         rtrn[2] = phase;
         return rtrn;
      } else {
         //TODO determine if it is an iteration or fork
         if (phase.matches("[0-9]+")) {
            rtrn[1] = phase;
            rtrn[2] = defaultName;
         } else {
            rtrn[1] = defaultName;
            rtrn[2] = phase;
         }
         return rtrn;
      }
   }

   private static void histogramArray(JsonGenerator jGenerator, Iterator<HistogramIterationValue> iter) throws IOException {
      jGenerator.writeStartArray(); //start histogram
      double from = -1, to = -1, percentileTo = -1;
      long total = 0;
      while (iter.hasNext()) {
         HistogramIterationValue iterValue = iter.next();
         if (iterValue.getCountAddedInThisIterationStep() == 0) {
            if (from < 0) {
               from = iterValue.getValueIteratedFrom();
               total = iterValue.getTotalCountToThisValue();
            } else {
               to = iterValue.getValueIteratedTo();
               percentileTo = iterValue.getPercentileLevelIteratedTo();
            }
         } else {
            if (from >= 0) {
               writeBucket(jGenerator, from, to, percentileTo, 0, total);
            }
            writeBucket(jGenerator,
                  iterValue.getDoubleValueIteratedFrom(),
                  iterValue.getDoubleValueIteratedTo(),
                  iterValue.getPercentileLevelIteratedTo(),
                  iterValue.getCountAddedInThisIterationStep(),
                  iterValue.getTotalCountToThisValue());
         }
      }
      if (from >= 0) {
         writeBucket(jGenerator, from, to, percentileTo, 0, total);
      }
      jGenerator.writeEndArray(); //end histogram
   }

   private static void writeBucket(JsonGenerator jGenerator, double from, double to, double percentile, long count, long totalCount) throws IOException {
      jGenerator.writeStartObject();
      jGenerator.writeNumberField("from", from);
      jGenerator.writeNumberField("to", to);
      jGenerator.writeNumberField("percentile", percentile / 100.0D);
      jGenerator.writeNumberField("count", count);
      jGenerator.writeNumberField("totalCount", totalCount);
      jGenerator.writeEndObject();
   }

   private static void seriesArray(JsonGenerator jGenerator, List<StatisticsSummary> series) throws IOException {
      jGenerator.writeStartArray(); //series
      if (series != null) {
         for (StatisticsSummary summary : series) {
            jGenerator.writeObject(summary);
         }
      }
      jGenerator.writeEndArray(); //end series
      jGenerator.flush();
   }

   private static void writeTotalValue(JsonGenerator generator, Data data, Function<Data, StatisticsSnapshot> selector, LowHigh lowHigh) throws IOException {
      StatisticsSnapshot snapshot = selector.apply(data);

      generator.writeStartObject();
      generator.writeStringField("phase", data.phase);
      generator.writeStringField("metric", data.metric);
      generator.writeNumberField("start", data.total.histogram.getStartTimeStamp());
      generator.writeNumberField("end", data.total.histogram.getEndTimeStamp());
      generator.writeObjectField("summary", snapshot.summary(StatisticsStore.PERCENTILES));
      generator.writeFieldName("custom");

      generator.writeStartObject();
      for (Map.Entry<Object, CustomValue> entry : snapshot.custom.entrySet()) {
         generator.writeStringField(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
      }
      generator.writeEndObject();

      if (lowHigh != null) {
         generator.writeNumberField("minSessions", lowHigh.low);
         generator.writeNumberField("maxSessions", lowHigh.high);
      }

      generator.writeEndObject();
   }

   private static void totalArray(
         JsonGenerator jGenerator,
         Data[] dataList,
         Map<String, StatisticsStore.SessionPoolStats> sessionPoolStats,
         Function<Data, StatisticsSnapshot> selector,
         Function<StatisticsStore.SessionPoolStats, LowHigh> sessionPoolStatsSelector
   ) throws IOException {
      jGenerator.writeStartArray();
      for (Data data : dataList) {
         StatisticsSnapshot snapshot = selector.apply(data);
         if (snapshot == null) {
            continue;
         }
         jGenerator.writeStartObject();
         jGenerator.writeStringField("phase", data.phase);
         jGenerator.writeStringField("metric", data.metric);
         jGenerator.writeNumberField("start", data.total.histogram.getStartTimeStamp());
         jGenerator.writeNumberField("end", data.total.histogram.getEndTimeStamp());
         jGenerator.writeObjectField("summary", snapshot.summary(StatisticsStore.PERCENTILES));
         jGenerator.writeFieldName("custom");

         jGenerator.writeStartObject();
         for (Map.Entry<Object, CustomValue> entry : snapshot.custom.entrySet()) {
            jGenerator.writeStringField(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
         }
         jGenerator.writeEndObject();

         StatisticsStore.SessionPoolStats sps = sessionPoolStats.get(data.phase);
         if (sps != null) {
            LowHigh lohi = sessionPoolStatsSelector.apply(sps);
            jGenerator.writeNumberField("minSessions", lohi.low);
            jGenerator.writeNumberField("maxSessions", lohi.high);
         }

         jGenerator.writeEndObject();
      }
      jGenerator.writeEndArray(); //end total.csv
   }

   private static <T> void walkPhaseIterFork(JsonGenerator jGenerator,
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
}
