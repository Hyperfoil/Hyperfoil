package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.impl.LocalSimulationRunner;

public class PhaseSessionsReleaseTest {
   private final AtomicReference<LocalSimulationRunner> runner = new AtomicReference<>();

   @Test
   public void testReleasedBeforeStrictlyDependentPhaseStarts() {
      assertThat(runCalibrationAndTest(true)).isEqualTo(2);
   }

   @Test
   public void testKeptWhenNotRequested() {
      assertThat(runCalibrationAndTest(false)).isEqualTo(4 + 2);
   }

   /**
    * @return the number of sessions referenced when the test phase starts
    */
   private int runCalibrationAndTest(boolean releaseSessions) {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().threads(3);
      AtomicInteger sessionsAtTestStart = new AtomicInteger(-1);
      // @formatter:off
      builder.addPhase("calibration").atOnce(4).releaseSessions(releaseSessions)
            .scenario().initialSequence("noop").step(session -> true);
      // startTime(0): the local runner polls without waiting, so it would start this phase as soon as it may
      builder.addPhase("test").atOnce(2).startTime(0).startAfterStrict("calibration")
            .scenario().initialSequence("count").step(session -> {
               sessionsAtTestStart.compareAndSet(-1, countSessions());
               return true;
            });
      // @formatter:on
      assertThat(run(builder)).isEqualTo(releaseSessions ? 2 : 4 + 2);
      return sessionsAtTestStart.get();
   }

   @Test
   public void testKeptForNextIteration() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().threads(3);
      AtomicInteger executions = new AtomicInteger();
      // @formatter:off
      builder.addPhase("loop").atOnce(2).maxIterations(2).releaseSessions(true)
            .scenario().initialSequence("count").step(session -> executions.incrementAndGet() > 0);
      // @formatter:on
      run(builder);
      // the second iteration found the sessions of the first one in the pool
      assertThat(executions.get()).isEqualTo(2 + 2);
   }

   @Test
   public void testKeptWhenAnotherPhaseIsRunning() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().threads(3);
      // @formatter:off
      builder.addPhase("background").always(1).duration(500)
            .scenario().initialSequence("noop").step(session -> true);
      builder.addPhase("calibration").atOnce(4).releaseSessions(true)
            .scenario().initialSequence("noop").step(session -> true);
      builder.addPhase("test").atOnce(2).startAfterStrict("calibration")
            .scenario().initialSequence("noop").step(session -> true);
      // @formatter:on
      assertThat(run(builder)).isEqualTo(1 + 4 + 2);
   }

   @Test
   public void testKeptWhenNextPhaseDoesNotStartStrictlyAfter() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().threads(3);
      // @formatter:off
      builder.addPhase("calibration").atOnce(4).releaseSessions(true)
            .scenario().initialSequence("noop").step(session -> true);
      builder.addPhase("test").atOnce(2).startTime(300)
            .scenario().initialSequence("noop").step(session -> true);
      // @formatter:on
      assertThat(run(builder)).isEqualTo(4 + 2);
   }

   /**
    * @return the number of sessions the runner references when the run is over
    */
   private int run(BenchmarkBuilder builder) {
      runner.set(new LocalSimulationRunner(builder.build()));
      runner.get().run();
      assertThat(runner.get().instances().values())
            .allMatch(phase -> phase.status() == PhaseInstance.Status.STATS_COMPLETE && phase.getError() == null);
      return countSessions();
   }

   private int countSessions() {
      AtomicInteger sessions = new AtomicInteger();
      runner.get().visitSessions(session -> sessions.incrementAndGet());
      return sessions.get();
   }
}
