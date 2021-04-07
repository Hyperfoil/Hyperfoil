package io.hyperfoil.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.hyperfoil.api.statistics.StatsExtension;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.builders.SLA;
import io.hyperfoil.core.util.LowHigh;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class CsvWriter {
   private static final Logger log = LogManager.getLogger(CsvWriter.class);

   public static void writeCsv(Path dir, StatisticsStore store) throws IOException {
      store.data.values().stream().flatMap(m -> m.values().stream()).filter(d -> !d.isCompleted()).findAny()
            .ifPresent(incomplete -> log.error("Phase {} metric {} was not completed!", incomplete.phase, incomplete.metric));
      File statsDir = dir.toFile();
      if (!statsDir.exists() && !statsDir.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted, Comparator.comparing((Data d) -> d.phase).thenComparing(d -> d.metric).thenComparingInt(d -> d.stepId));

      try (PrintWriter writer = new PrintWriter(dir + File.separator + "total.csv")) {
         writer.print("Phase,Metric,Start,End,");
         StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
         String[] extensionHeaders = getHeaders(Stream.of(sorted).map(d -> d.total.extensions));
         printExtensionHeaders(writer, extensionHeaders);
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
            data.total.summary(StatisticsStore.PERCENTILES).printTo(writer, extensionHeaders);

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
      String[] agents = store.data.values().stream()
            .flatMap(m -> m.values().stream())
            .flatMap(d -> d.perAgent.keySet().stream())
            .distinct().sorted().toArray(String[]::new);
      for (String agent : agents) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + "agent." + sanitize(agent) + ".csv")) {
            writer.print("Phase,Metric,Start,End,");
            StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
            String[] extensionHeaders = getHeaders(Stream.of(sorted).map(d -> d.perAgent.get(agent)).filter(Objects::nonNull).map(s -> s.extensions));
            printExtensionHeaders(writer, extensionHeaders);
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
               agentStats.summary(StatisticsStore.PERCENTILES).printTo(writer, extensionHeaders);

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
      }
      try (PrintWriter writer = new PrintWriter(dir + File.separator + "failures.csv")) {
         writer.print("Phase,Metric,Message,Start,End,");
         StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
         String[] extensionHeaders = getHeaders(store.failures.stream().map(f -> f.statistics().extensions));
         printExtensionHeaders(writer, extensionHeaders);
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
            summary.printTo(writer, extensionHeaders);
            writer.println();
         }
      }
      for (Map.Entry<String, StatisticsStore.SessionPoolStats> entry : store.sessionPoolStats.entrySet()) {
         try (PrintWriter writer = new PrintWriter(dir + File.separator + sanitize(entry.getKey()) + ".sessions.csv")) {
            writer.println("Timestamp,Address,MinSessions,MaxSessions");
            WriterUtil.printInSync(entry.getValue().records, (address, record) -> {
               writer.print(record.timestamp);
               writer.print(',');
               writer.print(address);
               writer.print(',');
               writer.print(record.low);
               writer.print(',');
               writer.println(record.high);
            });
         }
      }
      for (var targetEntry : store.connectionPoolStats.entrySet()) {
         for (var typeEntry : targetEntry.getValue().entrySet()) {
            try (PrintWriter writer = new PrintWriter(dir + File.separator + sanitize(targetEntry.getKey()) + "." + sanitize(typeEntry.getKey()) + ".connections.csv")) {
               writer.println("Timestamp,Address,MinConnections,MaxConnections");
               WriterUtil.printInSync(typeEntry.getValue(), (address, record) -> {
                  writer.print(record.timestamp);
                  writer.print(',');
                  writer.print(address);
                  writer.print(',');
                  writer.print(record.low);
                  writer.print(',');
                  writer.println(record.high);
               });
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
         String[] extensionHeaders = getHeaders(series.stream().map(ss -> ss.extensions));
         try (PrintWriter writer = new PrintWriter(filePrefix + ".series.csv")) {
            writer.print("Start,End,");
            StatisticsSummary.printHeader(writer, StatisticsStore.PERCENTILES);
            printExtensionHeaders(writer, extensionHeaders);
            writer.println();
            for (StatisticsSummary summary : series) {
               writer.print(summary.startTime);
               writer.print(',');
               writer.print(summary.endTime);
               writer.print(',');
               summary.printTo(writer, extensionHeaders);
               writer.println();
            }
         }
      }
   }

   private static void printExtensionHeaders(PrintWriter writer, String[] extensionHeaders) {
      for (String header : extensionHeaders) {
         writer.print(',');
         writer.print(header);
      }
   }

   private static String[] getHeaders(Stream<? extends Map<String, StatsExtension>> extensions) {
      return extensions.flatMap(ext ->
            ext.entrySet().stream().flatMap(c ->
                  Stream.of(c.getValue().headers()).map(h -> c.getKey() + "." + h)))
            .sorted().distinct().toArray(String[]::new);
   }
}
