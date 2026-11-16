package io.hyperfoil.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.PhaseBuilder;

public class WrkScenarioReleaseSessionsTest {

   @Test
   public void testWrk2ReleasesCalibrationSessions() throws Exception {
      Map<String, Boolean> release = releaseSessions(new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType, long durationMs) {
            return WrkScenarioPhaseConfig.wrk2PhaseConfig(catalog, phaseType, durationMs, 100);
         }
      });
      assertEquals(Map.of("calibration", true, "test", false), release);
   }

   @Test
   public void testWrkKeepsCalibrationSessions() throws Exception {
      // wrk's calibration only has one session per connection: a full GC would buy nothing
      Map<String, Boolean> release = releaseSessions(new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType, long durationMs) {
            return WrkScenarioPhaseConfig.wrkPhaseConfig(catalog, 10);
         }
      });
      assertEquals(Map.of("calibration", false, "test", false), release);
   }

   private static Map<String, Boolean> releaseSessions(WrkScenario scenario) throws Exception {
      Benchmark benchmark = scenario.getBenchmark("wrk", "http://localhost:8080/", false, 10, false, 2, null,
            "1s", "2s", null, "60s").build();
      return benchmark.phases().stream().collect(Collectors.toMap(Phase::name, Phase::releaseSessions));
   }
}
