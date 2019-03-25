package io.hyperfoil.maven;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME;

@Mojo(name = "run", defaultPhase = LifecyclePhase.INTEGRATION_TEST, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class RunMojo extends AbstractMojo {

    private static final Logger logger = Logger.getLogger(RunMojo.class);

    @Parameter(required = true)
    private File yaml;

    @Parameter(defaultValue = "false")
    private Boolean outputPercentileDistribution;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        //set logger impl
        System.setProperty(LOGGER_DELEGATE_FACTORY_CLASS_NAME, "io.vertx.core.logging.Log4j2LogDelegateFactory");

        logger.info("Start running Hyperfoil simulation");

        if (!yaml.exists())
            throw new MojoExecutionException("yaml not found: " + yaml.toPath());

        Benchmark benchmark = null;
        try {
            benchmark = buildBenchmark(new FileInputStream(yaml));

            if (benchmark != null) {
                LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
                logger.info("Running for " + benchmark.simulation().statisticsCollectionPeriod());
                logger.info(benchmark.simulation().threads() + " threads");
                runner.run();
                StatisticsCollector collector = new StatisticsCollector(benchmark.simulation());
                runner.visitStatistics(collector);
                collector.visitStatistics((phase, name, stats, countDown) -> printStats(stats), null);
            }
        } catch (FileNotFoundException e) {
            logger.error("Couldn't find yaml file: " + e.getMessage());
            throw new MojoExecutionException("yaml not found: " + yaml.toPath());
        }

        logger.info("Finished running simulation");
    }


    private Benchmark buildBenchmark(InputStream inputStream) throws MojoFailureException {
        if (inputStream == null)
            logger.error("Could not find benchmark configuration");

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String source = result.toString(StandardCharsets.UTF_8.name());
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source);

            if (benchmark == null)
                logger.info("Failed to parse benchmark configuration");

            return benchmark;
        } catch (ParserException | IOException e) {
            logger.error("Error occurred during parsing: " + e.getMessage());
            throw new MojoFailureException("Error occurred during parsing: " + e.getMessage(), e);
        }
    }

    private void printStats(StatisticsSnapshot stats) {
        double durationSeconds = (stats.histogram.getEndTimeStamp() - stats.histogram.getStartTimeStamp()) / 1000d;
        logger.info(stats.histogram.getTotalCount() + " requests in " + durationSeconds + "s, ");
        logger.info("                 Avg    Stdev      Max");
        logger.info("Latency:      " + formatTime(stats.histogram.getMean()) + " " + formatTime(stats.histogram.getStdDeviation()) + " " + formatTime(stats.histogram.getMaxValue()));
        logger.info("Requests/sec: " + stats.histogram.getTotalCount() / durationSeconds);

        if (outputPercentileDistribution) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                stats.histogram.outputPercentileDistribution(new PrintStream(baos, true, "UTF-8"), 1000.00);
                String data = new String(baos.toByteArray(), StandardCharsets.UTF_8);

                logger.info("\nPercentile Distribution\n\n" + data );
            } catch (UnsupportedEncodingException e) {
                logger.error("Could not write Percentile Distribution to log");
            }
        }

        if (stats.errors() > 0) {
            logger.info("Socket errors: connect " + stats.connectFailureCount + ", reset " + stats.resetCount + ", timeout " + stats.timeouts);
            logger.info("Non-2xx or 3xx responses: " + stats.status_4xx + stats.status_5xx + stats.status_other);
        }
    }

    private String formatData(double value) {
        double scaled;
        String suffix;
        if (value >= 1024 * 1024 * 1024) {
            scaled = (double) value / (1024 * 1024 * 1024);
            suffix = "GB";
        } else if (value >= 1024 * 1024) {
            scaled = (double) value / (1024 * 1024);
            suffix = "MB";
        } else if (value >= 1024) {
            scaled = (double) value / 1024;
            suffix = "kB";
        } else {
            scaled = value;
            suffix = "B ";
        }
        return String.format("%6.2f%s", scaled, suffix);
    }

    private String formatTime(double value) {
        String suffix = "ns";
        if (value >= 1000_000_000) {
            value /= 1000_000_000;
            suffix = "s ";
        } else if (value >= 1000_000) {
            value /= 1000_000;
            suffix = "ms";
        } else if (value >= 1000) {
            value /= 1000;
            suffix = "us";
        }
        return String.format("%6.2f%s", value, suffix);
    }

}
