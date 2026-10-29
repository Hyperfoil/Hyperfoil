package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.internal.Properties;

public class PhaseSessionsReleaseTest {
   private static final int CALIBRATION_USERS = 4;
   private static final int TEST_USERS = 2;

   @Test
   public void testTerminatedPhaseSessionsAreReleased() {
      assertThat(runAndCountSessions()).isEqualTo(TEST_USERS);
   }

   @Test
   public void testTerminatedPhaseSessionsAreKeptWhenRequested() {
      System.setProperty(Properties.KEEP_PHASE_SESSIONS, "true");
      try {
         assertThat(runAndCountSessions()).isEqualTo(CALIBRATION_USERS + TEST_USERS);
      } finally {
         System.clearProperty(Properties.KEEP_PHASE_SESSIONS);
      }
   }

   /**
    * Runs a phase and another one starting strictly after it (two session pools, as wrk2's calibration and test
    * phases) and returns how many sessions the runner still references when the run is over.
    */
   private int runAndCountSessions() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().threads(3);
      // @formatter:off
      builder.addPhase("calibration").atOnce(CALIBRATION_USERS)
            .scenario().initialSequence("noop").step(session -> true);
      builder.addPhase("test").atOnce(TEST_USERS).startAfterStrict("calibration")
            .scenario().initialSequence("noop").step(session -> true);
      // @formatter:on
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build());
      runner.run();
      assertThat(runner.instances().values()).allMatch(phase -> phase.status() == PhaseInstance.Status.STATS_COMPLETE);
      AtomicInteger sessions = new AtomicInteger();
      runner.visitSessions(session -> sessions.incrementAndGet());
      return sessions.get();
   }
}
