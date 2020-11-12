package io.hyperfoil.core.impl.statistics;

import com.fasterxml.jackson.core.JsonGenerator;

import io.hyperfoil.api.Version;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.util.LowHigh;

import io.vertx.core.json.JsonObject;

import org.HdrHistogram.HistogramIterationValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class JsonWriter {
   private static final String RUN_SCHEMA = "http://hyperfoil.io/run-schema/v2.0";

   public static void writeArrayJsons(StatisticsStore store, JsonGenerator jGenerator, JsonObject info) throws IOException {
      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data data) -> data.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      jGenerator.writeStartObject(); //root of object

      if (info != null && !info.isEmpty()) {
         jGenerator.writeFieldName("info");
         jGenerator.writeRawValue(info.encode());
      }
      jGenerator.writeStringField("$schema", RUN_SCHEMA);
      jGenerator.writeStringField("version", Version.VERSION);
      jGenerator.writeStringField("commit", Version.COMMIT_ID);

      jGenerator.writeFieldName("failures");
      jGenerator.writeStartArray();
      List<SLA.Failure> failures = store.getFailures();
      for (SLA.Failure failure : failures) {
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
         long numFailures = failures.stream().filter(f -> f.phase().equals(data.phase) && (f.metric() == null || f.metric().equals(data.metric))).count();
         StatisticsStore.SessionPoolStats sessionPoolStats = store.sessionPoolStats.get(data.phase);
         LowHigh minMaxSessions = sessionPoolStats == null ? new LowHigh(0, 0) : sessionPoolStats.findMinMax();
         writeTotalValue(jGenerator, data, d -> d.total, minMaxSessions, numFailures);

         jGenerator.writeFieldName("histogram");
         jGenerator.writeStartObject();
         jGenerator.writeFieldName("percentiles");
         histogramArray(jGenerator, data.total.histogram.percentiles(5).iterator(), 100);
         jGenerator.writeFieldName("linear");
         histogramArray(jGenerator, data.total.histogram.linearBucketValues(1_000_000).iterator(), 95);
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
            Map<String, List<StatisticsStore.SessionPoolRecord>> records = sps != null ? sps.records : Collections.emptyMap();
            String[] addresses = new String[records.size()];
            @SuppressWarnings("unchecked")
            Iterator<StatisticsStore.SessionPoolRecord>[] iterators = new Iterator[records.size()];
            int counter = 0;
            for (Map.Entry<String, List<StatisticsStore.SessionPoolRecord>> byAddress : records.entrySet()) {
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
                           .orElse(new LowHigh(0, 0)),
                     -1); // we don't track failures per agent

               jGenerator.writeFieldName("histogram");
               jGenerator.writeStartObject(); // histograms

               jGenerator.writeFieldName("percentiles");
               histogramArray(jGenerator, data.perAgent.get(agent).histogram.percentiles(5).iterator(), 100);

               jGenerator.writeFieldName("linear");
               histogramArray(jGenerator, data.perAgent.get(agent).histogram.linearBucketValues(1_000_000).iterator(), 95);

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
      } else {
         //TODO determine if it is an iteration or fork
         if (phase.matches("[0-9]+")) {
            rtrn[1] = phase;
            rtrn[2] = defaultName;
         } else {
            rtrn[1] = defaultName;
            rtrn[2] = phase;
         }
      }
      return rtrn;
   }

   private static void histogramArray(JsonGenerator jGenerator, Iterator<HistogramIterationValue> iter, double maxPercentile) throws IOException {
      jGenerator.writeStartArray(); //start histogram
      double from = -1, to = -1, percentileTo = -1;
      long total = 0;
      HistogramIterationValue iterValue = null;
      while (iter.hasNext()) {
         iterValue = iter.next();
         if (iterValue.getCountAddedInThisIterationStep() == 0) {
            if (from < 0) {
               from = iterValue.getValueIteratedFrom();
               total = iterValue.getTotalCountToThisValue();
            }
            to = iterValue.getValueIteratedTo();
            percentileTo = iterValue.getPercentileLevelIteratedTo();
         } else {
            if (from >= 0) {
               writeBucket(jGenerator, from, to, percentileTo, 0, total);
               from = -1;
            }
            writeBucket(jGenerator,
                  iterValue.getDoubleValueIteratedFrom(),
                  iterValue.getDoubleValueIteratedTo(),
                  iterValue.getPercentileLevelIteratedTo(),
                  iterValue.getCountAddedInThisIterationStep(),
                  iterValue.getTotalCountToThisValue());
         }
         if (iterValue.getPercentileLevelIteratedTo() > maxPercentile) {
            break;
         }
      }
      if (from >= 0) {
         writeBucket(jGenerator, from, to, percentileTo, 0, total);
      }
      if (iterValue != null) {
         from = iterValue.getDoubleValueIteratedTo();
         total = iterValue.getTotalCountToThisValue();
         while (iter.hasNext()) {
            iterValue = iter.next();
         }
         if (iterValue.getTotalCountToThisValue() != total) {
            writeBucket(jGenerator, from, iterValue.getDoubleValueIteratedTo(),
                  iterValue.getPercentileLevelIteratedTo(),
                  iterValue.getTotalCountToThisValue() - total,
                  iterValue.getTotalCountToThisValue());
         }
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

   private static void writeTotalValue(JsonGenerator generator, Data data, Function<Data, StatisticsSnapshot> selector, LowHigh minMaxSessions, long failures) throws IOException {
      StatisticsSnapshot snapshot = selector.apply(data);

      generator.writeStartObject();
      generator.writeStringField("phase", data.phase);
      generator.writeStringField("metric", data.metric);
      generator.writeNumberField("start", data.total.histogram.getStartTimeStamp());
      generator.writeNumberField("end", data.total.histogram.getEndTimeStamp());
      generator.writeObjectField("summary", snapshot.summary(StatisticsStore.PERCENTILES));
      if (failures >= 0) {
         generator.writeNumberField("failures", failures);
      }
      generator.writeFieldName("custom");

      generator.writeStartObject();
      for (Map.Entry<Object, CustomValue> entry : snapshot.custom.entrySet()) {
         generator.writeStringField(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
      }
      generator.writeEndObject();

      if (minMaxSessions != null) {
         generator.writeNumberField("minSessions", minMaxSessions.low);
         generator.writeNumberField("maxSessions", minMaxSessions.high);
      }

      generator.writeEndObject();
   }
}
