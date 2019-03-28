package io.hyperfoil.test;

import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.SimulationBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.core.builders.StepCatalog;

public class TestBenchmarks {
   public static SimulationBuilder addTestSimulation(BenchmarkBuilder builder, int users) {
      return builder.simulation()
            .http()
               .baseUrl("http://localhost:8080")
               .sharedConnections(10)
            .endHttp()
            .addPhase("test").always(users)
               .duration("5s")
               .scenario()
                  .initialSequence("test")
                        .step(StepCatalog.class).httpRequest(HttpMethod.GET)
                           .path("test")
                           .sla()
                              .addItem()
                                 .meanResponseTime(10, TimeUnit.MILLISECONDS)
                                 .addPercentileLimit(0.99, TimeUnit.MILLISECONDS.toNanos(100))
                                 .errorRate(0.02)
                                 .window(3000, TimeUnit.MILLISECONDS)
                              .endSLA()
                           .endList()
                        .endStep()
                        .step(StepCatalog.class).awaitAllResponses()
                  .endSequence()
               .endScenario()
            .endPhase();
   }

   public static Benchmark testBenchmark(int agents) {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder().name("test");
      for (int i = 0; i < agents; ++i) {
         benchmarkBuilder.addAgent("agent" + i, "localhost:12345");
      }
      addTestSimulation(benchmarkBuilder, agents);
      return benchmarkBuilder.build();
   }
}
