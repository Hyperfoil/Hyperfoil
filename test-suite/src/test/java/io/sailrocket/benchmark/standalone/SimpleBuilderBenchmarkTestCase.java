package io.sailrocket.benchmark.standalone;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.core.impl.Report;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.impl.LocalSimulationRunner;
import io.sailrocket.test.TestBenchmarks;

import org.HdrHistogram.Histogram;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


@Category(io.sailrocket.test.Benchmark.class)
public class SimpleBuilderBenchmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() {

        BenchmarkBuilder builder = BenchmarkBuilder.builder().name("Test Benchmark");
        TestBenchmarks.addTestSimulation(builder);
        Benchmark benchmark = builder.build();
        Simulation simulation = benchmark.simulation();

        assertEquals("http://localhost:8080/", simulation.tags().get("url"));
        assertEquals(10, simulation.tags().get("maxQueue"));
        assertEquals(10, simulation.tags().get("connections"));
        assertEquals(5_000L, simulation.phases().stream().findFirst().get().duration());

        try {
            Map<String, Report> reports = new LocalSimulationRunner(benchmark).run();
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
