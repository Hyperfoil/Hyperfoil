package io.hyperfoil.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.HdrHistogram.Histogram;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.api.statistics.StatsExtension;
import io.hyperfoil.core.builders.SLA;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonLoader {

   public static StatisticsStore read(String text, StatisticsStore store) {
      JsonObject object = new JsonObject(text);
      String schema = object.getString("$schema");
      if (!JsonWriter.RUN_SCHEMA.equals(schema)) {
         throw new IllegalArgumentException("Schema " + schema + " is not recognized.");
      }
      for (Object item : object.getJsonArray("failures")) {
         JsonObject failure = (JsonObject) item;
         StatisticsSnapshot snapshot = new StatisticsSnapshot();
         snapshot.histogram.setStartTimeStamp(failure.getLong("start"));
         snapshot.histogram.setEndTimeStamp(failure.getLong("end"));
         // ignoring percentiles
         store.addFailure(new SLA.Failure(null, failure.getString("phase"), failure.getString("metric"), snapshot, failure.getString("message")));
      }
      int dataCounter = 0;
      // TODO: there could be multiple Data per phase+metric but stepId is not in JSON
      Map<String, Map<String, Data>> dataMap = new HashMap<>();
      for (Object item : object.getJsonArray("stats")) {
         JsonObject stats = (JsonObject) item;
         Data data = new Data(store, stats.getString("name"), stats.getBoolean("isWarmup"), 0, stats.getString("metric"), Collections.emptyMap(), new SLA[0]);
         dataMap.computeIfAbsent(data.phase, p -> new HashMap<>()).putIfAbsent(data.metric, data);
         store.addData(dataCounter++, data.metric, data);
         loadSnapshot(stats.getJsonObject("total"), data.total);
         // We cannot use percentiles histogram since it always tells only upper bounds on the response time
         // and the results would be way of (at least for the first bucket)
         loadHistogram(stats.getJsonObject("histogram").getJsonArray("linear"), data.total.histogram);
         loadSeries(stats.getJsonArray("series"), data.series);
      }

      for (Object item : object.getJsonArray("sessions")) {
         JsonObject ss = (JsonObject) item;
         StatisticsStore.SessionPoolStats sps = new StatisticsStore.SessionPoolStats();
         store.sessionPoolStats.put(ss.getString("name"), sps);
         for (Object r : ss.getJsonArray("sessions")) {
            JsonObject record = (JsonObject) r;
            String agent = record.getString("agent");
            long timestamp = record.getLong("timestamp");
            int min = record.getInteger("minSessions");
            int max = record.getInteger("maxSessions");
            StatisticsStore.SessionPoolRecord ssRecord = new StatisticsStore.SessionPoolRecord(timestamp, min, max);
            sps.records.computeIfAbsent(agent, a -> new ArrayList<>()).add(ssRecord);
         }
      }

      for (Object item : object.getJsonArray("agents")) {
         JsonObject agent = (JsonObject) item;
         String name = agent.getString("name");
         for (Object s : agent.getJsonArray("stats")) {
            JsonObject stats = (JsonObject) s;
            String phase = stats.getString("name");
            String metric = stats.getString("metric");
            boolean isWarmup = stats.getBoolean("isWarmup");
            Data data = dataMap.computeIfAbsent(phase, p -> new HashMap<>())
                  .computeIfAbsent(metric, m -> new Data(store, phase, isWarmup, 0, metric, Collections.emptyMap(), new SLA[0]));
            StatisticsSnapshot snapshot = new StatisticsSnapshot();
            loadSnapshot(stats.getJsonObject("total"), snapshot);
            loadHistogram(stats.getJsonObject("histogram").getJsonArray("linear"), snapshot.histogram);
            data.perAgent.put(name, snapshot);
            loadSeries(stats.getJsonArray("series"), data.agentSeries.computeIfAbsent(name, a -> new ArrayList<>()));
         }
      }

      for (var targetEntry : object.getJsonObject("connections")) {
         String target = targetEntry.getKey();
         var targetMap = store.connectionPoolStats.computeIfAbsent(target, t -> new HashMap<>());
         for (var typeEntry : (JsonObject) targetEntry.getValue()) {
            String type = typeEntry.getKey();
            var typeMap = targetMap.computeIfAbsent(type, t -> new HashMap<>());
            for (Object item : (JsonArray) typeEntry.getValue()) {
               JsonObject record = (JsonObject) item;
               List<StatisticsStore.ConnectionPoolStats> list = typeMap.computeIfAbsent(record.getString("agent"), a -> new ArrayList<>());
               list.add(new StatisticsStore.ConnectionPoolStats(record.getLong("timestamp"), record.getInteger("min"), record.getInteger("max")));
            }
         }
      }

      return store;
   }

   private static void loadSnapshot(JsonObject object, StatisticsSnapshot total) {
      total.histogram.setStartTimeStamp(object.getLong("start"));
      total.histogram.setEndTimeStamp(object.getLong("end"));
      JsonObject summary = object.getJsonObject("summary");
      total.requestCount = summary.getInteger("requestCount");
      total.responseCount = summary.getInteger("responseCount");
      total.invalid = summary.getInteger("invalid");
      total.connectionErrors = summary.getInteger("connectionErrors");
      total.requestTimeouts = summary.getInteger("requestTimeouts");
      total.internalErrors = summary.getInteger("internalErrors");
      total.blockedTime = summary.getLong("blockedTime");
      JsonObject extensions = object.getJsonObject("extensions");
      if (extensions != null && extensions.isEmpty()) {
         // TODO: load extensions
      }
   }

   private static void loadHistogram(JsonArray array, Histogram histogram) {
      for (Object item : array) {
         JsonObject bucket = (JsonObject) item;
         long from = bucket.getLong("from");
         long to = bucket.getLong("to");
         long count = bucket.getLong("count");
         // should we use arithmetic or geometric mean in here?
         long mid = (from + to) / 2;
         histogram.recordValueWithCount(mid, count);
      }
   }

   private static void loadSeries(JsonArray array, List<StatisticsSummary> series) {
      for (Object item : array) {
         JsonObject object = (JsonObject) item;
         long startTime = object.getLong("startTime");
         long endTime = object.getLong("endTime");
         long minResponseTime = object.getLong("minResponseTime");
         long meanResponseTime = object.getLong("meanResponseTime");
         long maxResponseTime = object.getLong("maxResponseTime");
         int requestCount = object.getInteger("requestCount");
         int responseCount = object.getInteger("responseCount");
         int invalid = object.getInteger("invalid");
         int connectionErrors = object.getInteger("connectionErrors");
         int requestTimeouts = object.getInteger("requestTimeouts");
         int internalErrors = object.getInteger("internalErrors");
         long blockedTime = object.getLong("blockedTime");

         SortedMap<String, StatsExtension> extensions = Collections.emptySortedMap(); // TODO
         SortedMap<Double, Long> percentiles = toMap(object.getJsonObject("percentileResponseTime"));
         series.add(new StatisticsSummary(startTime, endTime, minResponseTime, meanResponseTime, maxResponseTime, percentiles, requestCount, responseCount, invalid, connectionErrors, requestTimeouts, internalErrors, blockedTime, extensions));
      }
   }

   private static SortedMap<Double, Long> toMap(JsonObject object) {
      TreeMap<Double, Long> map = new TreeMap<>();
      for (var entry : object) {
         map.put(Double.parseDouble(entry.getKey()), Long.parseLong(String.valueOf(entry.getValue())));
      }
      return map;
   }
}
