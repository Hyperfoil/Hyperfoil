package io.sailrocket.benchmark.standalone;

import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Report;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.impl.SimulationImpl;
import io.sailrocket.test.Benchmark;
import org.HdrHistogram.Histogram;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Map;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


@Category(Benchmark.class)
public class SimpleBuilderBenchmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() {

        SimulationImpl simulation = simulationBuilder()
                .http(httpBuilder().baseUrl("http://localhost:8080"))
                .concurrency(10)
                .connections(10)
                .addPhase("foo").always(1)
                    .duration("10s")
                    .scenario(scenarioBuilder()
                        .initialSequence(sequenceBuilder()
                                .step().httpRequest(HttpMethod.GET)
                                        .path("foo")
                                        .endStep()
                                .step().awaitAllResponses()
                                .end()
                        )
                    )
                .endPhase()
                .build();

        assertEquals("http://localhost:8080/", simulation.tags().getString("url"));
        assertEquals(10, simulation.tags().getInteger("maxQueue").intValue());
        assertEquals(10, simulation.tags().getInteger("connections").intValue());
        assertEquals(10_000L, simulation.phases().stream().findFirst().get().duration());

        BenchmarkImpl benchmark =
                BenchmarkBuilder.builder()
                        .name("Test Benchmark")
                        .simulation(simulation)
                        .build();

        try {
            Map<String, Report> reports = benchmark.run();
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
