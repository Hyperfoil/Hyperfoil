package io.hyperfoil.clustering;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.GlobalData;
import io.hyperfoil.controller.StatisticsStore;
import io.hyperfoil.impl.Util;
import io.vertx.core.Promise;

class Run {
   final String id;
   final Path dir;
   Benchmark benchmark;
   final Map<String, ControllerPhase> phases = new HashMap<>();
   final List<AgentInfo> agents = new ArrayList<>();
   final Phase[] phasesById;
   final List<Error> errors = new ArrayList<>();
   final List<RunHookOutput> hookResults = new ArrayList<>();
   long deployTimerId;
   String description;

   long startTime = Long.MIN_VALUE;
   Promise<Long> terminateTime = Promise.promise();
   boolean cancelled;
   boolean completed;
   boolean validation;
   Supplier<StatisticsStore> statsSupplier;
   private StatisticsStore statisticsStore;
   Map<String, GlobalData.Element> newGlobalData = new HashMap<>();

   Run(String id, Path dir, Benchmark benchmark) {
      this (id, dir, benchmark, false);
   }

   Run(String id, Path dir, Benchmark benchmark, Boolean validation) {
      this.id = id;
      this.dir = dir;
      this.benchmark = benchmark;
      this.phasesById = benchmark.phasesById();
      this.validation = validation;
   }

   void initStore(StatisticsStore store) {
      this.statisticsStore = store;
   }

   long nextTimestamp() {
      long nextPhaseStart = phases.values().stream()
            .filter(phase -> phase.status() == ControllerPhase.Status.NOT_STARTED && phase.definition().startTime() >= 0)
            .mapToLong(phase -> startTime + phase.definition().startTime()).min().orElse(Long.MAX_VALUE);
      long nextPhaseFinish = phases.values().stream()
            .filter(phase -> phase.status() == ControllerPhase.Status.RUNNING)
            .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().duration()).min().orElse(Long.MAX_VALUE);
      long nextPhaseTerminate = phases.values().stream()
            .filter(phase -> (phase.status() == ControllerPhase.Status.RUNNING || phase.status() == ControllerPhase.Status.FINISHED) && phase.definition().maxDuration() >= 0)
            .mapToLong(phase -> phase.absoluteStartTime() + phase.definition().maxDuration()).min().orElse(Long.MAX_VALUE);
      return Math.min(Math.min(nextPhaseStart, nextPhaseFinish), nextPhaseTerminate);
   }

   ControllerPhase[] getAvailablePhases() {
      return phases.values().stream().filter(phase -> phase.status() == ControllerPhase.Status.NOT_STARTED &&
            startTime + phase.definition().startTime() <= System.currentTimeMillis() &&
            phase.definition().startAfter().stream().allMatch(dep -> phases.get(dep).status().isFinished()) &&
            phase.definition().startAfterStrict().stream().allMatch(dep -> phases.get(dep).status().isTerminated()) &&
            (phase.definition().startWithDelay() == null ||
                  phases.get(phase.definition().startWithDelay().phase).status().isStarted() &&
                  phases.get(phase.definition().startWithDelay().phase).absoluteStartTime() + phase.definition().startWithDelay().delay <= System.currentTimeMillis()))
            .toArray(ControllerPhase[]::new);
   }

   public String phase(int phaseId) {
      if (phaseId < phasesById.length) {
         return phasesById[phaseId].name();
      } else {
         return null;
      }
   }

   public StatisticsStore statisticsStore() {
      if (statisticsStore != null) {
         return statisticsStore;
      } else if (statsSupplier != null) {
         return statisticsStore = statsSupplier.get();
      } else {
         return null;
      }
   }

   public boolean isLoaded() {
      return statisticsStore != null;
   }

   public void unload() {
      statisticsStore = null;
   }

   public static class Error {
      public final AgentInfo agent;
      public final Throwable error;

      public Error(AgentInfo agent, Throwable error) {
         this.agent = agent;
         this.error = error;
      }

      @Override
      public String toString() {
         return (agent == null ? "" : agent.name + ": ") + Util.explainCauses(error);
      }
   }

   public static class RunHookOutput {
      public final String name;
      public final String output;

      public RunHookOutput(String name, String output) {
         this.name = name;
         this.output = output;
      }
   }
}
