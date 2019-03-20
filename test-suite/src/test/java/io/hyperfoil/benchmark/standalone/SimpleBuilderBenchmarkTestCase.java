package io.hyperfoil.benchmark.standalone;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.core.impl.Report;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.ReportStatisticsCollector;
import io.hyperfoil.test.TestBenchmarks;

import org.HdrHistogram.Histogram;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


@Category(io.hyperfoil.test.Benchmark.class)
public class SimpleBuilderBenchmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() {

        BenchmarkBuilder builder = BenchmarkBuilder.builder().name("Test Benchmark");
        TestBenchmarks.addTestSimulation(builder, 1);
        Benchmark benchmark = builder.build();
        Simulation simulation = benchmark.simulation();

        assertEquals("http://localhost:8080/", simulation.tags().get("url"));
        assertEquals(5_000L, simulation.phases().stream().findFirst().get().duration());

        try {
            LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
            runner.run();
            ReportStatisticsCollector statisticsConsumer = new ReportStatisticsCollector(simulation);
            runner.visitStatistics(statisticsConsumer);
            Map<String, Report> reports = statisticsConsumer.reports();
            assertNotEquals(0, reports.size());
            Histogram histogram = reports.values().stream().findFirst().get().histogram;
            assertNotEquals(0, histogram.getTotalCount());
            assertNotEquals(1, histogram.getTotalCount());
        } catch (BenchmarkDefinitionException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
