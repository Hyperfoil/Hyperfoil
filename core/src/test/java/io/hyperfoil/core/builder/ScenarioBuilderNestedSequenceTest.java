package io.hyperfoil.core.builder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.steps.LoopStep;
import io.hyperfoil.core.steps.StopwatchBeginStep;

/**
 * Verifies that only steps whose sequence is directly connected to the session
 * start (i.e. an initial sequence) are recognized by
 * {@link ScenarioBuilder#isFirstStepInInitialSequence}. Steps in loop bodies,
 * stopwatch blocks, or non-initial sequences are not — even when they are the
 * first step in their own sequence.
 */
public class ScenarioBuilderNestedSequenceTest {

   private static class DummyStepBuilder implements StepBuilder<DummyStepBuilder> {
      @Override
      public List<Step> build() {
         return Collections.singletonList(s -> true);
      }
   }

   private static StepBuilder<?> dummyStep() {
      return new DummyStepBuilder();
   }

   // A step in a loop body belongs to the LoopSequenceBuilder, not the initial
   // SequenceBuilder. On iterations 2+ the session start time is stale.
   @Test
   public void stepInLoopBody() {
      StepBuilder<?> innerStep = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      SequenceBuilder initial = scenario.initialSequence("init");
      // @formatter:on

      LoopStep.Builder loop = new LoopStep.Builder(initial);
      initial.stepBuilder(loop);
      loop.steps().stepBuilder(innerStep);

      assertTrue(scenario.isFirstStepInInitialSequence(initial, loop));
      assertFalse(scenario.isFirstStepInInitialSequence(loop.steps(), innerStep));
   }

   // A step in a stopwatch block belongs to the StopwatchBeginStep.Builder
   // sequence, not the initial SequenceBuilder.
   @Test
   public void stepInStopwatchBlock() {
      StepBuilder<?> innerStep = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      SequenceBuilder initial = scenario.initialSequence("init");
      // @formatter:on

      StopwatchBeginStep.Builder stopwatch = new StopwatchBeginStep.Builder(initial);
      initial.stepBuilder(stopwatch);
      stopwatch.stepBuilder(innerStep);

      assertTrue(scenario.isFirstStepInInitialSequence(initial, stopwatch));
      assertFalse(scenario.isFirstStepInInitialSequence(stopwatch, innerStep));
   }

   // A non-initial sequence is triggered by a step in another sequence — not
   // directly by the rate generator. The delay between session start and this
   // sequence running is unknown.
   @Test
   public void stepInNonInitialSequenceTriggeredFromInitial() {
      StepBuilder<?> step = dummyStep();
      // @formatter:off
      ScenarioBuilder scenario = BenchmarkBuilder.builder()
            .addPhase("test").constantRate(10)
               .duration(1000).maxSessions(10)
            .scenario();
      scenario.initialSequence("init")
            .stepBuilder(dummyStep());
      scenario.sequence("triggered")
            .stepBuilder(step);
      // @formatter:on

      assertFalse(scenario.isFirstStepInInitialSequence(scenario.findSequence("triggered"), step));
   }
}
