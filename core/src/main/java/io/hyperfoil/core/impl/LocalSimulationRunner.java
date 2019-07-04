package io.hyperfoil.core.impl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;

public class LocalSimulationRunner extends SimulationRunnerImpl {
   private final StatisticsCollector.StatisticsConsumer statsConsumer;
   private final SessionStatsConsumer sessionPoolStatsConsumer;
   private final Lock statusLock = new ReentrantLock();
   private final Condition statusCondition = statusLock.newCondition();
   private long startTime;

   public LocalSimulationRunner(Benchmark benchmark) {
      this(benchmark, null, null);
   }

   public LocalSimulationRunner(Benchmark benchmark, StatisticsCollector.StatisticsConsumer statsConsumer, SessionStatsConsumer sessionPoolStatsConsumer) {
      super(benchmark, null);
      this.statsConsumer = statsConsumer;
      this.sessionPoolStatsConsumer = sessionPoolStatsConsumer;
   }

   public void run() {
      if (benchmark.phases().isEmpty()) {
         throw new BenchmarkDefinitionException("No phases/scenarios have been defined");
      }

      CountDownLatch latch = new CountDownLatch(1);
      init(result -> latch.countDown());
      try {
         latch.await();
         // Exec is blocking and therefore must not run on the event-loop thread
         exec();
         for (PhaseInstance phase : instances.values()) {
            if (phase.getError() != null) {
               throw new RuntimeException(phase.getError());
            }
         }
      } catch (InterruptedException e) {
         throw new RuntimeException(e);
      } finally {
         shutdown();
      }
   }

   private void exec() {
      long now = System.currentTimeMillis();
      this.startTime = now;
      do {
         now = System.currentTimeMillis();
         for (PhaseInstance phase : instances.values()) {
            if (phase.status() == PhaseInstance.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
               finishPhase(phase.definition().name());
            }
            if (phase.status() == PhaseInstance.Status.FINISHED) {
               if (phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
                  terminatePhase(phase.definition().name());
               } else if (phase.definition().terminateAfterStrict().stream().map(instances::get).allMatch(p -> p.status() == PhaseInstance.Status.TERMINATED)) {
                  tryTerminatePhase(phase.definition().name());
               }
            }
         }
         PhaseInstance[] availablePhases = getAvailablePhases();
         for (PhaseInstance phase : availablePhases) {
            startPhase(phase.definition().name());
         }
         long nextPhaseStart = instances.values().stream()
               .filter(phase -> phase.status() == PhaseInstance.Status.NOT_STARTED && phase.definition().startTime() >= 0)
               .mapToLong(phase -> this.startTime + phase.definition().startTime()).min().orElse(Long.MAX_VALUE);
         long nextPhaseFinish = instances.values().stream()
               .filter(phase -> phase.status() == PhaseInstance.Status.RUNNING)
               .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().duration()).min().orElse(Long.MAX_VALUE);
         long nextPhaseTerminate = instances.values().stream()
               .filter(phase -> (phase.status() == PhaseInstance.Status.RUNNING || phase.status() == PhaseInstance.Status.FINISHED) && phase.definition().maxDuration() >= 0)
               .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().maxDuration()).min().orElse(Long.MAX_VALUE);
         long delay = Math.min(Math.min(nextPhaseStart, nextPhaseFinish), nextPhaseTerminate) - System.currentTimeMillis();

         delay = Math.min(delay, 1000);
         if (delay > 0) {
            statusLock.lock();
            try {
               statusCondition.await(delay, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
               for (PhaseInstance phase : instances.values()) {
                  terminatePhase(phase.definition().name());
               }
               Thread.currentThread().interrupt();
            } finally {
               statusLock.unlock();
            }
         }
      } while (instances.values().stream().anyMatch(phase -> phase.status() != PhaseInstance.Status.TERMINATED));
   }

   @Override
   protected void phaseChanged(Phase phase, PhaseInstance.Status status, Throwable error) {
      super.phaseChanged(phase, status, error);
      if (status == PhaseInstance.Status.TERMINATED) {
         publishStats(phase);
      }
      statusLock.lock();
      try {
         statusCondition.signal();
      } finally {
         statusLock.unlock();
      }
   }

   private void publishStats(Phase phase) {
      if (statsConsumer != null) {
         StatisticsCollector collector = new StatisticsCollector(benchmark);
         visitStatistics(phase, collector);
         collector.visitStatistics(statsConsumer, null);
      }
      if (sessionPoolStatsConsumer != null) {
         visitSessionPoolStats(phase, sessionPoolStatsConsumer);
      }
   }

   private PhaseInstance[] getAvailablePhases() {
      return instances.values().stream().filter(phase -> phase.status() == PhaseInstance.Status.NOT_STARTED &&
            startTime + phase.definition().startTime() <= System.currentTimeMillis() &&
            phase.definition().startAfter().stream().allMatch(dep -> instances.get(dep).status().isFinished()) &&
            phase.definition().startAfterStrict().stream().allMatch(dep -> instances.get(dep).status() == PhaseInstance.Status.TERMINATED))
            .toArray(PhaseInstance[]::new);
   }
}
