package io.sailrocket.core.impl;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.sailrocket.api.Benchmark;
import io.sailrocket.api.BenchmarkDefinitionException;
import io.sailrocket.api.Report;
import io.sailrocket.core.api.PhaseInstance;
import io.sailrocket.core.impl.statistics.PrintStatisticsConsumer;
import io.sailrocket.core.impl.statistics.ReportStatisticsCollector;

// maybe this should be just a test class
public class LocalSimulationRunner extends SimulationRunnerImpl {
   private Lock statusLock = new ReentrantLock();
   private Condition statusCondition = statusLock.newCondition();
   private long startTime;

   public LocalSimulationRunner(Benchmark benchmark) {
      super(benchmark.simulation());
   }

   public Map<String, Report> run() {
      if (simulation.phases().isEmpty()) {
         throw new BenchmarkDefinitionException("No phases/scenarios have been defined");
      }

      PrintStatisticsConsumer printStatsConsumer = new PrintStatisticsConsumer(simulation);

      Timer timer = new Timer("console-logger", true);
//      timer.schedule(new java.util.TimerTask() {
//         @Override
//         public void run() {
//            System.out.println("Statistics: ");
//            visitSessions(printStatsConsumer);
//            printStatsConsumer.print();
//         }
//      }, TimeUnit.SECONDS.toMillis(3), TimeUnit.SECONDS.toMillis(3));

      Map<String, Report> reports;
      try {
         init(this::phaseChanged);
         reports = exec();
         shutdown();
         timer.cancel();
         return reports;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   private Map<String, Report> exec() {
      long now = System.currentTimeMillis();
      this.startTime = now;
      do {
         now = System.currentTimeMillis();
         for (PhaseInstance phase : instances.values()) {
            if (phase.status() == PhaseInstance.Status.RUNNING && phase.absoluteStartTime() + phase.definition().duration() <= now) {
               finishPhase(phase.definition().name());
            }
            if (phase.status() == PhaseInstance.Status.FINISHED && phase.definition().maxDuration() >= 0 && phase.absoluteStartTime() + phase.definition().maxDuration() <= now) {
               terminatePhase(phase.definition().name());
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
               Thread.currentThread().interrupt();
            } finally {
               statusLock.unlock();
            }
         }
      } while (instances.values().stream().anyMatch(phase -> phase.status() != PhaseInstance.Status.TERMINATED));

      ReportStatisticsCollector statisticsConsumer = new ReportStatisticsCollector(simulation);
      visitSessions(statisticsConsumer);
      return statisticsConsumer.reports();
   }

   private void phaseChanged(String phase, PhaseInstance.Status status) {
      statusLock.lock();
      try {
         statusCondition.signal();
      } finally {
         statusLock.unlock();
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
