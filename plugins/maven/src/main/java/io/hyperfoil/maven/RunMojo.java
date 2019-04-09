package io.hyperfoil.maven;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.steps.BaseStep;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.util.Util;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

@Mojo(name = "run", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RunMojo extends AbstractMojo {

    private static final Logger log;

    @Parameter(required = true, property = "hyperfoil.yaml")
    private File yaml;

    @Parameter(defaultValue = "false", property = "hyperfoil.percentiles")
    private Boolean outputPercentileDistribution;

    private ExecutorService printStatsExecutor = Executors.newFixedThreadPool(1, r -> new Thread(r, "statistics"));
    private Benchmark benchmark;

    static {
        System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");
        log = LoggerFactory.getLogger(RunMojo.class);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        log.info("Start running Hyperfoil simulation");

        if (!yaml.exists())
            throw new MojoExecutionException("yaml not found: " + yaml.toPath());

        try {
            benchmark = buildBenchmark(new FileInputStream(yaml));

            if (benchmark != null) {
                // We want to log all stats in the same thread to not break the output layout too much.
                LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, this::printStats, this::printSessionPoolInfo);
                log.info("Running for {}", benchmark.statisticsCollectionPeriod());
                log.info("{} threads", benchmark.threads());
                runner.run();
            }
        } catch (FileNotFoundException e) {
            log.error("Couldn't find yaml file: {}", e, yaml);
            throw new MojoExecutionException("yaml not found: " + yaml.toPath());
        }

        printStatsExecutor.shutdown();
        log.info("Finished running simulation");
    }

    private void printSessionPoolInfo(String phase, int min, int max) {
        printStatsExecutor.execute(() -> log.info("Phase {} used {} - {} sessions.", phase, min, max));
    }


    private Benchmark buildBenchmark(InputStream inputStream) throws MojoFailureException {
        if (inputStream == null)
            log.error("Could not find benchmark configuration");

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String source = result.toString(StandardCharsets.UTF_8.name());
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, new LocalBenchmarkData());

            if (benchmark == null)
                log.info("Failed to parse benchmark configuration");

            return benchmark;
        } catch (ParserException | IOException e) {
            log.error("Error occurred during parsing", e);
            throw new MojoFailureException("Error occurred during parsing: " + e.getMessage(), e);
        }
    }

    private void printStats(int stepId, String statsName, StatisticsSnapshot snapshot, CountDown ignored) {
        StatisticsSnapshot copy = snapshot.clone();
        printStatsExecutor.submit(() -> printStats(stepId, statsName, copy));
    }

    private void printStats(int stepId, String statsName, StatisticsSnapshot stats) {
        String phase = benchmark.steps()
              .filter(BaseStep.class::isInstance).map(BaseStep.class::cast)
              .filter(s -> s.id() == stepId).map(s -> s.sequence().phase().name()).findFirst().get();
        double durationSeconds = (stats.histogram.getEndTimeStamp() - stats.histogram.getStartTimeStamp()) / 1000d;
        log.info("{}/{}: ", phase, statsName);
        log.info("{} requests in {} s, ", stats.histogram.getTotalCount(), durationSeconds);
        log.info("                  Avg     Stdev       Max");
        log.info("Latency:    {} {} {}", Util.prettyPrintNanos((long) stats.histogram.getMean()),
              Util.prettyPrintNanos((long) stats.histogram.getStdDeviation()), Util.prettyPrintNanos(stats.histogram.getMaxValue()));
        log.info("Requests/sec: {}", String.format("%.2f", stats.histogram.getTotalCount() / durationSeconds));

        if (outputPercentileDistribution) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                stats.histogram.outputPercentileDistribution(new PrintStream(baos, true, "UTF-8"), 1000.00);
                String data = new String(baos.toByteArray(), StandardCharsets.UTF_8);

                log.info("\nPercentile Distribution\n\n" + data );
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
