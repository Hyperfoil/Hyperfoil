package io.sailrocket.benchmark.standalone;

import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.test.Benchmark;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;


@Category(Benchmark.class)
public class SimpleBechmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() throws Exception {

        BenchmarkImpl benchmark =
                BenchmarkBuilder.builder()
                        .name("Simple Benchmark")
                        .simulation(simulationBuilder()
                                            .http(httpBuilder().baseUrl("http://localhost:8080/"))
                                            .concurrency(10)
                                            .duration("3s")
                                            .connections(1)
                                            .rate(100)
                                            )
                        .build();

//        benchmark.endpoint("/");

        try {
            benchmark.run();
        } catch (BenchmarkDefinitionException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
