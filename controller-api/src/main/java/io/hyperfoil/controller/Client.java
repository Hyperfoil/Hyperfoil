package io.hyperfoil.controller;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.controller.model.CustomStats;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.controller.model.Version;

/**
 * API for server control
 */
public interface Client {
   BenchmarkRef register(Benchmark benchmark);

   List<String> benchmarks();

   BenchmarkRef benchmark(String name);

   List<Run> runs(boolean details);

   RunRef run(String id);

   long ping();

   Version version();

   Collection<String> agents();

   String downloadLog(String node, String logId, long offset, String destinationFile);

   interface BenchmarkRef {
      String name();

      Benchmark get();

      RunRef start(String description);
   }

   interface RunRef {
      String id();

      Run get();

      RunRef kill();

      Benchmark benchmark();

      Map<String, Map<String, MinMax>> sessionStatsRecent();

      Map<String, Map<String, MinMax>> sessionStatsTotal();

      // TODO: server should expose JSON-formatted variants
      Collection<String> sessions();

      Collection<String> connections();

      RequestStatisticsResponse statsRecent();

      RequestStatisticsResponse statsTotal();

      void statsAll(String format, String destinationFile);

      Histogram histogram(String phase, int stepId, String metric);

      Collection<CustomStats> customStats();
   }

   class MinMax {
      public final int min;
      public final int max;

      @JsonCreator
      public MinMax(@JsonProperty("min") int min, @JsonProperty("max") int max) {
         this.min = min;
         this.max = max;
      }
   }

}
