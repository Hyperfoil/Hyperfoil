package io.sailrocket.benchmark.standalone;

import io.sailrocket.core.BenchmarkImpl;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.builders.HttpBuilder;
import io.sailrocket.core.builders.SimulationBuilder;
import io.sailrocket.test.Benchmark;
import org.junit.Test;
import org.junit.experimental.categories.Category;


@Category(Benchmark.class)
public class SimpleBechmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() throws Exception {

        BenchmarkImpl benchmark =
                BenchmarkBuilder.builder()
                        .name("Simple Benchmark")
                        .simulation(SimulationBuilder.builder()
                                            .http(HttpBuilder.builder().baseUrl("http://localhost:8080/").build())
                                            .concurrency(10)
                                            .duration("3s")
                                            .connections(1)
                                            .rate(100)
                                            .build())
                        .build();

        benchmark.endpoint("/");

        benchmark.run();
    }
}
