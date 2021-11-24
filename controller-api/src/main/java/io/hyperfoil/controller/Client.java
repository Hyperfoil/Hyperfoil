package io.hyperfoil.controller;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.controller.model.Version;

/**
 * API for server control
 */
public interface Client {
   BenchmarkRef register(Benchmark benchmark, String prevVersion);

   BenchmarkRef register(String benchmarkFile, Map<String, Path> otherFiles, String prevVersion, String storedFilesBenchmark);

   BenchmarkRef registerLocal(String benchmarkUri, String prevVersion, String storedFilesBenchmark);

   List<String> benchmarks();

   BenchmarkRef benchmark(String name);

   List<Run> runs(boolean details);

   RunRef run(String id);

   long ping();

   Version version();

   Collection<String> agents();

   String downloadLog(String node, String logId, long offset, File destinationFile);

   void shutdown(boolean force);

   interface BenchmarkRef {
      String name();

      BenchmarkSource source();

      Benchmark get();

      RunRef start(String description);

      String structure(Integer maxCollectionSize);
   }

   class BenchmarkSource {
      public final String source;
      public final String version;

      public BenchmarkSource(String source, String version) {
         this.source = source;
         this.version = version;
      }
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

      Map<String, Map<String, MinMax>> connectionStatsRecent();

      Map<String, Map<String, MinMax>> connectionStatsTotal();

      RequestStatisticsResponse statsRecent();

      RequestStatisticsResponse statsTotal();

      byte[] statsAll(String format);

      Histogram histogram(String phase, int stepId, String metric);

      byte[] file(String filename);

      byte[] report(String source);

      Map<String, Map<String, String>> agentCpu();
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

   class EditConflictException extends RuntimeException {
   }
}
