package io.hyperfoil.benchmark.standalone;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.impl.SimulationRunner;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;

public class SubMillisecondTimingTest extends BaseBenchmarkTest {

   @Test
   public void testSubMillisecondDetectionWithForks() {

      // Create a constantRate phase with 3000 req/sec total
      // Split across 4 forks with weights: 0.25, 0.25, 0.25, 0.25
      // Each fork will get 750 req/sec (below 1000 threshold)
      // But total is 3000 req/sec (above 1000 threshold)
      int rate = 3000;
      Duration duration = Duration.ofSeconds(1);
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("SubMillisecondBugReproducer")
            .addPlugin(HttpPluginBuilder::new).http()
            .host("localhost").port(httpServer.actualPort())
            .sharedConnections(50)
            .endHttp().endPlugin();

      PhaseBuilder.Catalog catalog = builder.addPhase("testPhase");
      PhaseBuilder.ConstantRate constantRate = catalog.constantRate(rate).variance(false);
      constantRate.duration(duration.toMillis());
      constantRate.maxSessions(rate * 5);

      // Add 4 forks with equal weight (0.25 each)
      int forks = 4;
      for (int i = 0; i < forks; i++) {
         constantRate.fork("fork" + i)
               .weight(1 / (double) forks)
               .scenario()
               .initialSequence("sequence" + i)
               .step(SC).httpRequest(HttpMethod.GET)
               .path("/test" + i)
               .endStep()
               .endSequence();
      }

      Benchmark benchmark = builder.build();

      // checkNativeTransportForSubMillisecondTiming() will check each fork
      // and see 750 req/sec (below threshold), so it returns false
      // because each phase has its own FireTimeSequence.
      // you can use LocalSimulationRunner if you wish to see it being executed
      SimulationRunner r = new SimulationRunner(benchmark, "0000", 0, (e) -> {
      });
      boolean requiresSubMillisecondPrecision = r.checkNativeTransportForSubMillisecondTiming();
      assertFalse(requiresSubMillisecondPrecision);
   }

   @Test
   public void testSubMillisecondDetection() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("SubMillisecondBugReproducer")
            .addPlugin(HttpPluginBuilder::new).http()
            .host("localhost").port(httpServer.actualPort())
            .sharedConnections(50)
            .endHttp().endPlugin();

      PhaseBuilder.Catalog catalog = builder.addPhase("testPhase");
      PhaseBuilder.ConstantRate constantRate = catalog.constantRate(3000);
      constantRate.duration(10000);
      constantRate.maxSessions(100);

      // Single scenario without forks - use scenario() directly
      constantRate.scenario()
            .initialSequence("sequence")
            .step(SC).httpRequest(HttpMethod.GET)
            .path("/test")
            .endStep()
            .endSequence();

      Benchmark benchmark = builder.build();
      SimulationRunner r = new SimulationRunner(benchmark, "0000", 0, (e) -> {
      });
      boolean requiresSubMillisecondPrecision = r.checkNativeTransportForSubMillisecondTiming();
      assertTrue(requiresSubMillisecondPrecision);
   }
}
