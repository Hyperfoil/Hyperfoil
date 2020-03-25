package io.hyperfoil.core.impl.statistics;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.util.LowHigh;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class CsvWriter {
   private static final Logger log = LoggerFactory.getLogger(CsvWriter.class);

   public static void writeCsv(Path dir, StatisticsStore store) throws IOException {
      Optional<Data> incomplete = store.data.values().stream().flatMap(m -> m.values().stream()).filter(d -> !d.isCompleted()).findAny();
      if (incomplete.isPresent()) {
         log.error("Phase {} metric {} was not completed!", incomplete.get().phase, incomplete.get().metric);
      }
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data d) -> d.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Metric,Start,End,");
         StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
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
            data.total.summary(StatisticsStore.PERCENTILES).printTo(writer);

            StatisticsStore.SessionPoolStats sps = store.sessionPoolStats.get(data.phase);
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
      for (Data data : sorted) {
         String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.metric) + "." + data.stepId;
         writeHistogramAndSeries(filePrefix, data.total, data.series);
      }
      writeCustomStats(sorted, data -> data.total, dir + File.separator + "custom.csv");
      String[] agents = store.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Metric,Start,End,");
            StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
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
               agentStats.summary(StatisticsStore.PERCENTILES).printTo(writer);

               StatisticsStore.SessionPoolStats sps = store.sessionPoolStats.get(data.phase);
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
         for (Data data : sorted) {
            String filePrefix = dir + File.separator + sanitize(data.phase) + "." + sanitize(data.metric) + "." + data.stepId + ".agent." + agent;
            writeHistogramAndSeries(filePrefix, data.perAgent.get(agent), data.agentSeries.get(agent));
         }
         writeCustomStats(sorted, data -> data.perAgent.get(agent), dir + File.separator + "agent." + sanitize(agent) + ".custom.csv");
      }
      try (PrintWriter writer = new PrintWriter(dir + File.separator + "failures.csv")) {
         writer.print("Phase,Metric,Message,Start,End,");
         StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
         writer.println();
         for (SLA.Failure failure : store.failures) {
            writer.print(failure.phase());
            writer.print(',');
            writer.print(failure.metric());
            writer.print(",\"");
            writer.print(failure.message());
            writer.print("\",");
            StatisticsSummary summary = failure.statistics().summary(StatisticsStore.PERCENTILES);
            writer.print(summary.startTime);
            writer.print(',');
            writer.print(summary.endTime);
            writer.print(',');
            summary.printTo(writer);
            writer.println();
         }
      }
      for (Map.Entry<String, StatisticsStore.SessionPoolStats> entry : store.sessionPoolStats.entrySet()) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + sanitize(entry.getKey()) + ".sessions.csv")) {
            StatisticsStore.SessionPoolStats sps = entry.getValue();
            writer.println("Timestamp,Address,MinSessions,MaxSessions");
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
            do {
               hadNext = false;
               for (int i = 0; i < addresses.length; ++i) {
                  if (iterators[i].hasNext()) {
                     StatisticsStore.SessionPoolRecord record = iterators[i].next();
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

   private static void writeCustomStats(Data[] sorted, Function<Data, StatisticsSnapshot> selector, String fileName) throws FileNotFoundException {
      try (PrintWriter writer = new PrintWriter(fileName)) {
         writer.println("Phase,Metric,Custom,Value");
         for (Data data : sorted) {
            StatisticsSnapshot snapshot = selector.apply(data);
            for (Map.Entry<Object, CustomValue> entry : snapshot.custom.entrySet()) {
               writer.print(data.phase);
               writer.print(',');
               writer.print(data.metric);
               writer.print(',');
               writer.print(entry.getKey());
               writer.print(',');
               writer.println(entry.getValue());
            }
         }
      }
   }

   private static String sanitize(String phase) {
      return phase.replaceAll(File.separator, "_");
   }

   private static void writeHistogramAndSeries(String filePrefix, StatisticsSnapshot total, List<StatisticsSummary> series) throws FileNotFoundException {
      if (total != null) {
         try (PrintStream stream = new PrintStream(new FileOutputStream(filePrefix + ".histogram.csv"))) {
            total.histogram.outputPercentileDistribution(stream, 5, 1000_000.0, true);
         }
      }
      if (series != null) {
         try (PrintWriter writer = new PrintWriter(filePrefix + ".series.csv")) {
            writer.print("Start,End,");
            StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
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
}
