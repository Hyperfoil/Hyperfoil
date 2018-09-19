package io.sailrocket.test;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;

import java.util.concurrent.TimeUnit;

import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.config.Benchmark;
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
                        .sla()
                           .meanResponseTime(TimeUnit.MILLISECONDS.toNanos(10))
                           .addPercentileLimit(0.99, TimeUnit.MILLISECONDS.toNanos(100))
                           .errorRate(0.02)
                           .window(3000)
                        .endSLA()
                        .step().httpRequest(HttpMethod.GET)
                        .path("test")
                        .endStep()
                        .step().awaitAllResponses()
                  .endSequence()
               .endScenario()
            .endPhase();
   }

   public static Benchmark testBenchmark(int agents) {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder().name("test");
      for (int i = 0; i < agents; ++i) {
         benchmarkBuilder.addAgent("agent" + i, "agent", 12345);
      }
      addTestSimulation(benchmarkBuilder);
      return benchmarkBuilder.build();
   }
}
