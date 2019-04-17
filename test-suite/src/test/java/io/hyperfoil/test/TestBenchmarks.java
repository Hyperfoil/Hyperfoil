package io.hyperfoil.test;

import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.core.builders.StepCatalog;

public class TestBenchmarks {
   public static BenchmarkBuilder addTestSimulation(BenchmarkBuilder builder, int users, int port) {
      return builder
            .http()
               .baseUrl("http://localhost:" + port)
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
                  .endSequence()
               .endScenario()
            .endPhase();
   }

   public static Benchmark testBenchmark(int agents, int port) {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder().name("test");
      for (int i = 0; i < agents; ++i) {
         benchmarkBuilder.addAgent("agent" + i, "localhost", null);
      }
      addTestSimulation(benchmarkBuilder, agents, port);
      return benchmarkBuilder.build();
   }
}
