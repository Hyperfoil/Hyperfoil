package io.hyperfoil.api.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ScenarioBuilderTest {

   private static class DummyStepBuilder implements StepBuilder<DummyStepBuilder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(s -> true);
      }
   }

   private static StepBuilder<?> dummyStep() {
      return new DummyStepBuilder();
   }

   // The first step in an initial sequence fires immediately when the rate generator
   // creates a session — the fire time accurately represents when this step executes.
   @Test
   public void firstStepInInitialSequence() {
      StepBuilder<?> step = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      scenario.initialSequence("init")
            .stepBuilder(step)
            .stepBuilder(dummyStep());
      // @formatter:on

      assertTrue(scenario.isFirstStepInInitialSequence(scenario.findSequence("init"), step));
   }

   // The second step executes after the first completes — there's already elapsed
   // time since session start, so the fire time no longer matches this step's actual
   // execution time.
   @Test
   public void secondStepInInitialSequenceIsNotFirst() {
      StepBuilder<?> secondStep = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      scenario.initialSequence("init")
            .stepBuilder(dummyStep())
            .stepBuilder(secondStep);
      // @formatter:on

      assertFalse(scenario.isFirstStepInInitialSequence(scenario.findSequence("init"), secondStep));
   }

   // Non-initial sequences are triggered explicitly by steps in other sequences,
   // not by the rate generator. There's an unknown delay between session start
   // and when this sequence actually runs — the fire time is stale.
   @Test
   public void firstStepInNonInitialSequence() {
      StepBuilder<?> step = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      scenario.initialSequence("init")
            .stepBuilder(dummyStep());
      scenario.sequence("other")
            .stepBuilder(step);
      // @formatter:on

      assertFalse(scenario.isFirstStepInInitialSequence(scenario.findSequence("other"), step));
   }

   // Multiple initial sequences all start at the same fire time — they all launch
   // simultaneously at session creation, so the first step in each one is first
   // in an initial sequence.
   @Test
   public void firstStepInParallelInitialSequences() {
      StepBuilder<?> stepA = dummyStep();
      StepBuilder<?> stepB = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      scenario.initialSequence("first")
            .stepBuilder(stepA);
      scenario.initialSequence("second")
            .stepBuilder(stepB);
      // @formatter:on

      assertTrue(scenario.isFirstStepInInitialSequence(scenario.findSequence("first"), stepA));
      assertTrue(scenario.isFirstStepInInitialSequence(scenario.findSequence("second"), stepB));
   }

   // In a closed model phase the topology check passes (it is a first step in an
   // initial sequence), but hasOpenModelPhase() is false — there is no rate generator
   // producing fire times, so there is no scheduled start time to inherit.
   @Test
   public void closedModelIsFirstInInitialButNotOpenModel() {
      StepBuilder<?> step = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").atOnce(1)
               .duration(1000)
            .scenario();
      scenario.initialSequence("init")
            .stepBuilder(step);
      // @formatter:on

      assertTrue(scenario.isFirstStepInInitialSequence(scenario.findSequence("init"), step));
      assertFalse(scenario.hasOpenModelPhase());
   }
}
