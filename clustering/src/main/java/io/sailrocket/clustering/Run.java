package io.sailrocket.clustering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.impl.statistics.StatisticsStore;

class Run {
   final String id;
   final Benchmark benchmark;
   final Map<String, ControllerPhase> phases = new HashMap<>();
   final List<AgentInfo> agents;

   long startTime = Long.MIN_VALUE;
   long terminateTime = Long.MIN_VALUE;
   StatisticsStore statisticsStore;


   Run(String id, Benchmark benchmark, List<AgentInfo> agents) {
      this.id = id;
      this.benchmark = benchmark;
      this.agents = agents;
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
            phase.definition().startAfterStrict().stream().allMatch(dep -> phases.get(dep).status().isTerminated()))
            .toArray(ControllerPhase[]::new);
   }
}
