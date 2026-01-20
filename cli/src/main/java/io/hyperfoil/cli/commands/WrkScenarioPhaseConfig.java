package io.hyperfoil.cli.commands;

import io.hyperfoil.api.config.PhaseBuilder;

/**
 * Configuration utility for wrk and wrk2 benchmark scenario phases.
 * <p>
 * Provides factory methods to create phase configurations compatible with wrk-style
 * benchmarks. This class is used by both the CLI commands and test classes to ensure
 * consistent phase configuration across different execution contexts.
 */
public class WrkScenarioPhaseConfig {

   public static PhaseBuilder<?> wrkPhaseConfig(PhaseBuilder.Catalog catalog, int connections) {
      return catalog.always(connections);
   }

   public static PhaseBuilder<?> wrk2PhaseConfig(PhaseBuilder.Catalog catalog, WrkScenario.PhaseType phaseType,
         long durationMs, int rate) {

      int durationSeconds = (int) Math.ceil(durationMs / 1000);
      int maxSessions = switch (phaseType) {
         case calibration -> rate * durationSeconds;
         case test -> rate * 15;
      };
      return catalog.constantRate(rate)
            .variance(false)
            .maxSessions(maxSessions);
   }
}
