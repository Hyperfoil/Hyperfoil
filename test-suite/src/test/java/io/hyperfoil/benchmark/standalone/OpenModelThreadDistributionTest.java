package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.impl.LocalSimulationRunner;

/**
 * Reproducer for <a href="https://github.com/Hyperfoil/Hyperfoil/issues/627">#627</a>:
 * Open models risk running single-threaded when the SUT is slow.
 * <p>
 * Uses a constantRate phase with a high rate and a small maxSessions to force throttling
 * and catch-up. Each session records which Hyperfoil event loop thread it executes on.
 * The test asserts that no single thread handles a disproportionate share of sessions.
 */
@Tag("io.hyperfoil.test.Benchmark")
public class OpenModelThreadDistributionTest {

   private static final int THREADS = 4;
   private static final int DELAY_MS = 50;
   private static final double MAX_THREAD_RATIO = 0.60;

   static final ConcurrentHashMap<String, LongAdder> sessionsPerThread = new ConcurrentHashMap<>();

   private static final Action RECORD_THREAD = session -> sessionsPerThread
         .computeIfAbsent(Thread.currentThread().getName(), k -> new LongAdder())
         .increment();

   @Test
   public void testSessionsDistributedAcrossThreadsDuringCatchUp() {
      sessionsPerThread.clear();
      // High rate + small maxSessions + delay = guaranteed throttling and catch-up
      int usersPerSec = 200;
      int maxSessions = 20;
      int durationMs = 5000;

      // @formatter:off
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder()
            .name("open-model-thread-distribution")
            .threads(THREADS);

      PhaseBuilder.ConstantRate phaseBuilder = benchmarkBuilder.addPhase("test").constantRate(usersPerSec)
            .duration(durationMs)
            .maxSessions(maxSessions);

      SequenceBuilder sequence = phaseBuilder.scenario().initialSequence("request");
      sequence.step(StepCatalog.class).action((Action.Builder) () -> RECORD_THREAD);
      sequence.step(StepCatalog.class).thinkTime(DELAY_MS, TimeUnit.MILLISECONDS);
      sequence.endSequence();
      // @formatter:on

      Benchmark benchmark = benchmarkBuilder.build();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
      runner.run();

      long totalSessions = sessionsPerThread.values().stream().mapToLong(LongAdder::sum).sum();
      System.out.println("Session distribution (" + totalSessions + " total across " + THREADS + " threads):");
      for (var entry : sessionsPerThread.entrySet()) {
         double ratio = (double) entry.getValue().sum() / totalSessions;
         System.out.printf("  %s: %d sessions (%.1f%%)%n", entry.getKey(), entry.getValue().sum(), ratio * 100);
         assertTrue(ratio < MAX_THREAD_RATIO,
               String.format("Thread %s handled %.1f%% of sessions (expected < %.0f%%). " +
                     "Catch-up sessions are clustering on a single event loop (issue #627).",
                     entry.getKey(), ratio * 100, MAX_THREAD_RATIO * 100));
      }
   }
}
