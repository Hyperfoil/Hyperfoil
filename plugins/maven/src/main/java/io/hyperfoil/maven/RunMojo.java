package io.hyperfoil.maven;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;

@Mojo(name = "run", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RunMojo extends AbstractMojo {

   private static final Logger log = LogManager.getLogger(RunMojo.class);

   @Parameter(required = true, property = "hyperfoil.yaml")
   private File yaml;

   @Parameter(defaultValue = "false", property = "hyperfoil.percentiles")
   private Boolean outputPercentileDistribution;

   private Benchmark benchmark;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      log.info("Start running Hyperfoil simulation");

      if (!yaml.exists())
         throw new MojoExecutionException("yaml not found: " + yaml.toPath());

      // TODO: as we're aggregating snapshots for the same stage we're printing the stats only at the end
      HashMap<String, StatisticsSnapshot> total = new HashMap<>();
      try {
         benchmark = buildBenchmark(new FileInputStream(yaml), yaml.toPath());

         if (benchmark != null) {
            // We want to log all stats in the same thread to not break the output layout too much.
            LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, (phase, stepId, metric, snapshot, ignored) -> {
               snapshot.addInto(total.computeIfAbsent(phase.name() + "/" + metric, k -> new StatisticsSnapshot()));
            }, this::printSessionPoolInfo, null);
            log.info("Running for {}", benchmark.statisticsCollectionPeriod());
            log.info("{} threads", benchmark.defaultThreads());
            runner.run();
         }
      } catch (FileNotFoundException e) {
         log.error("Couldn't find yaml file: " + yaml, e);
         throw new MojoExecutionException("yaml not found: " + yaml.toPath());
      }
      total.forEach(this::printStats);

      log.info("Finished running simulation");
   }

   private void printSessionPoolInfo(String phase, int min, int max) {
      log.info("Phase {} used {} - {} sessions.", phase, min, max);
   }


   private Benchmark buildBenchmark(InputStream inputStream, Path path) throws MojoFailureException {
      if (inputStream == null)
         log.error("Could not find benchmark configuration");

      try {
         String source = Util.toString(inputStream);
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, new LocalBenchmarkData(path));

         if (benchmark == null)
            log.info("Failed to parse benchmark configuration");

         return benchmark;
      } catch (ParserException | IOException e) {
         log.error("Error occurred during parsing", e);
         throw new MojoFailureException("Error occurred during parsing: " + e.getMessage(), e);
      }
   }

   private void printStats(String phaseAndMetric, StatisticsSnapshot stats) {
      double durationSeconds = (stats.histogram.getEndTimeStamp() - stats.histogram.getStartTimeStamp()) / 1000d;
      log.info("{}: ", phaseAndMetric);
      log.info("{} requests in {} s, ", stats.histogram.getTotalCount(), durationSeconds);
      log.info("                  Avg     Stdev       Max");
      log.info("Latency:    {} {} {}", Util.prettyPrintNanosFixed((long) stats.histogram.getMean()),
            Util.prettyPrintNanosFixed((long) stats.histogram.getStdDeviation()), Util.prettyPrintNanosFixed(stats.histogram.getMaxValue()));
      log.info("Requests/sec: {}", String.format("%.2f", stats.histogram.getTotalCount() / durationSeconds));

      if (outputPercentileDistribution) {
         final ByteArrayOutputStream baos = new ByteArrayOutputStream();
         try {
            stats.histogram.outputPercentileDistribution(new PrintStream(baos, true, "UTF-8"), 1000.00);
            String data = new String(baos.toByteArray(), StandardCharsets.UTF_8);

            log.info("\nPercentile Distribution\n\n" + data);
         } catch (UnsupportedEncodingException e) {
            log.error("Could not write Percentile Distribution to log");
         }
      }

      if (stats.errors() > 0) {
         log.info("Socket errors: connect {}, reset {}, timeout {}", stats.connectFailureCount, stats.resetCount, stats.timeouts);
         log.info("Non-2xx or 3xx responses: {}", stats.status_4xx + stats.status_5xx + stats.status_other);
      }
   }
}
