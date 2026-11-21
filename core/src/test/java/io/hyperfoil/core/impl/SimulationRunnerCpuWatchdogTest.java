package io.hyperfoil.core.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseInstance;

class SimulationRunnerCpuWatchdogTest {
   @Test
   void onlyActiveMeasurementPhasesEnableCpuErrors() throws Exception {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("cpu-watchdog").threads(1);
      for (String name : List.of("warmup", "measurement", "overlapping", "later-warmup", "later-measurement")) {
         builder.addPhase(name).always(1).duration(60000).isWarmup(name.contains("warmup"))
               .scenario().initialSequence("wait").step(session -> false);
      }
      TestRunner runner = new TestRunner(builder.build());
      try {
         runner.init();
         assertFalse(runner.isCpuWatchdogEnabled(), "Preallocated measurement phases have not started");

         runner.startPhase("warmup");
         assertFalse(runner.isCpuWatchdogEnabled(), "Warmup must not enable CPU errors");

         runner.startPhase("measurement");
         assertTrue(runner.isCpuWatchdogEnabled(), "A measurement overlapping warmup must still be checked");
         runner.finishPhase("measurement");
         assertTrue(runner.isCpuWatchdogEnabled(), "Finished injection can still have draining sessions");

         runner.startPhase("overlapping");
         runner.stopPhase("measurement");
         assertTrue(runner.isCpuWatchdogEnabled(), "Another measurement is still active");
         runner.stopPhase("overlapping");
         assertFalse(runner.isCpuWatchdogEnabled(), "Terminated and future measurements must not enable CPU errors");

         runner.stopPhase("warmup");
         runner.startPhase("later-warmup");
         assertFalse(runner.isCpuWatchdogEnabled(), "A later warmup must also be excluded");
         runner.startPhase("later-measurement");
         assertTrue(runner.isCpuWatchdogEnabled());
         runner.stopPhase("later-measurement");
         runner.stopPhase("later-warmup");
         assertFalse(runner.isCpuWatchdogEnabled(), "No measurement remains active");
      } finally {
         runner.shutdown();
         runner.eventLoopGroup.terminationFuture().sync();
      }
   }

   private static class TestRunner extends SimulationRunner {
      private final ConcurrentHashMap<String, CompletableFuture<Void>> terminated = new ConcurrentHashMap<>();

      TestRunner(Benchmark benchmark) {
         super(benchmark, "test", 0, error -> {
            throw new AssertionError(error);
         });
      }

      @Override
      protected Runnable onPhaseChanged(Phase phase, PhaseInstance.Status status) {
         return () -> {
            if (status == PhaseInstance.Status.TERMINATED) {
               terminated.computeIfAbsent(phase.name, ignored -> new CompletableFuture<>()).complete(null);
            }
         };
      }

      void stopPhase(String name) throws Exception {
         CompletableFuture<Void> completion = terminated.computeIfAbsent(name, ignored -> new CompletableFuture<>());
         terminatePhase(name);
         completion.get(10, TimeUnit.SECONDS);
      }
   }
}
