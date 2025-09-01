package io.hyperfoil.controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HdrWriter {
   private static final Logger log = LogManager.getLogger(HdrWriter.class);

   public static void writeHdr(Path dir, StatisticsStore store) throws IOException {
      WriterUtil.createLocalDir(dir);

      Data[] sorted = store.data.values().stream().flatMap(map -> map.values().stream()).toArray(Data[]::new);
      Arrays.sort(sorted,
            Comparator.comparing((Data d) -> d.total.histogram.getStartTimeStamp()).thenComparing(d -> d.phase)
                  .thenComparing(d -> d.total.histogram.getEndTimeStamp()).thenComparing(d -> d.metric)
                  .thenComparingInt(d -> d.stepId));

      try (PrintStream totalStream = new PrintStream(dir + File.separator + "total.hgrm")) {
         // global hgrm containing all histograms for all phases
         HistogramLogWriter histLogWriter = new HistogramLogWriter(totalStream);
         histLogWriter.outputLogFormatVersion();
         histLogWriter.outputLegend();

         for (Data data : sorted) {
            try (PrintStream byPhaseStream = new PrintStream(
                  dir + File.separator + WriterUtil.sanitize(data.phase) + "." + WriterUtil.sanitize(data.metric)
                        + ".hgrm")) {

               for (Map.Entry<String, List<Histogram>> agentIntervalHistograms : data.intervalHistogramsPerAgent.entrySet()) {
                  agentIntervalHistograms.getValue().forEach(hist -> {
                     hist.setTag(getTag(agentIntervalHistograms.getKey(), data));
                     histLogWriter.outputIntervalHistogram(hist);
                  });
               }

               // load the merged intervals among all agents if more than one agent is configured
               if (data.intervalHistogramsPerAgent.size() > 1) {
                  data.intervalHistograms.forEach(hist -> {
                     hist.setTag(getTag("merged", data));
                     histLogWriter.outputIntervalHistogram(hist);
                  });
               }

               // save the phase-specific histogram
               data.total.histogram.outputPercentileDistribution(byPhaseStream, 1.0);
            }
         }
      }
   }

   private static String getTag(String agent, Data d) {
      return agent + "/" + d.phase + "/" + d.metric + "/" + (d.isWarmup ? "warmup" : "");
   }
}
