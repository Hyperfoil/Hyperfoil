package io.sailrocket.test;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.builders.SimulationBuilder;

public class TestBenchmarks {
   public static SimulationBuilder addTestSimulation(BenchmarkBuilder builder) {
      return builder.simulation()
            .http(httpBuilder().baseUrl("http://localhost:8080"))
            .concurrency(10)
            .connections(10)
            .addPhase("test").always(1)
               .duration("5s")
               .scenario()
                  .initialSequence("test")
                        .step().httpRequest(HttpMethod.GET)
                        .path("test")
                        .endStep()
                        .step().awaitAllResponses()
                  .endSequence()
               .endScenario()
            .endPhase();
   }

   public static io.sailrocket.api.Benchmark testBenchmark() {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder().name("test");
      addTestSimulation(benchmarkBuilder);
      return benchmarkBuilder.build();
   }
}
