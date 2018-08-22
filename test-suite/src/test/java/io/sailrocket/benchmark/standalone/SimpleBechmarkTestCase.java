package io.sailrocket.benchmark.standalone;

import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.test.Benchmark;
import org.HdrHistogram.Histogram;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Collection;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;
import static io.sailrocket.core.builders.StepBuilder.stepBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


@Category(Benchmark.class)
public class SimpleBechmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() throws Exception {

        SimulationImpl simulation = simulationBuilder()
                .http(httpBuilder().baseUrl("http://localhost:8080"))
                .concurrency(10)
                .connections(10)
                .duration("10s")
                .rate(101)
                .scenario(scenarioBuilder()
                        .sequence(sequenceBuilder()
                                .step(stepBuilder()
                                        .path("foo")
                                )
                        )
                )
                .build();

        assertEquals("http://localhost:8080/", simulation.tags().getString("url"));
        assertEquals(10, simulation.tags().getInteger("maxQueue").intValue());
        assertEquals(10, simulation.tags().getInteger("connections").intValue());
        assertEquals(101, simulation.tags().getInteger("rate").intValue());
        assertEquals(10000000000L, simulation.duration());

        BenchmarkImpl benchmark =
                BenchmarkBuilder.builder()
                        .name("Test Benchmark")
                        .simulation(simulation)
                        .build();

        try {
            Collection<Report> reports = benchmark.run();
            assertNotEquals(0, reports.size());
            Histogram histogram = reports.stream().findFirst().get().histogram;
            assertNotEquals(0, histogram.getTotalCount());
            assertNotEquals(1, histogram.getTotalCount());
        } catch (BenchmarkDefinitionException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
