package io.hyperfoil.core.sequences;

import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.handlers.NewSequenceAction;
import io.hyperfoil.core.session.BaseScenarioTest;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class OrderedSequenceTest extends BaseScenarioTest {

   @Test
   public void orderedSequenceTest() {
      Benchmark benchmark = loadScenario("scenarios/ordered-sequence.hf.yaml");
      verifySequenceOrdering(benchmark, NextSequenceType.NEXT_SEQUENCE_STEP);
      runScenario(benchmark);
   }

   private static void verifySequenceOrdering(Benchmark benchmark, NextSequenceType nextSequenceType) {
      Phase[] phases = benchmark.phases().toArray(new Phase[0]);
      assertEquals(1, phases.length);
      Phase phase = phases[0];
      // check that the first, second and third sequences are correctly linked via nextSequence
      Sequence[] initialSequences = phase.scenario().initialSequences();
      assertEquals(1, initialSequences.length);
      Sequence first = initialSequences[0];
      validateOrder(first, "first", "second", nextSequenceType);
      assertEquals(3, phase.scenario().sequences().length);
      assertEquals(first, phase.scenario().sequences()[0]);
      validateOrder(phase.scenario().sequences()[1], "second", "third", nextSequenceType);
      assertEquals("third", phase.scenario().sequences()[2].name());
   }

   private enum NextSequenceType {
      NEW_SEQUENCE_ACTION,
      NEXT_SEQUENCE_STEP
   }

   private static void validateOrder(Sequence current, String currentName, String expectedNextName, NextSequenceType expectedNextType) {
      assertEquals(currentName, current.name());
      Step lastStep = current.steps()[current.steps().length - 1];
      switch (expectedNextType) {
         case NEW_SEQUENCE_ACTION:
            assertTrue(lastStep instanceof StepBuilder.ActionStep);
            StepBuilder.ActionStep actionStep = (StepBuilder.ActionStep) lastStep;
            assertTrue(actionStep.action() instanceof NewSequenceAction);
            NewSequenceAction newSequenceAction = (NewSequenceAction) actionStep.action();
            assertEquals(expectedNextName, newSequenceAction.sequenceName());
            break;
         case NEXT_SEQUENCE_STEP:
            assertTrue(lastStep instanceof SequenceBuilder.NextSequenceStep);
            SequenceBuilder.NextSequenceStep nextSequenceStep = (SequenceBuilder.NextSequenceStep) lastStep;
            assertEquals(expectedNextName, nextSequenceStep.sequenceName());
            break;
         default:
            fail("Unknown next sequence type: " + expectedNextType);
      }
   }

   @Test
   public void implicitOrderedSequenceTest() {
      Benchmark benchmark = loadScenario("scenarios/implicit-ordered-sequence.hf.yaml");
      verifySequenceOrdering(benchmark, NextSequenceType.NEXT_SEQUENCE_STEP);
      runScenario(benchmark);
   }

   @Test
   public void orderedSequenceUsingInitialSequenceTest() {
      Benchmark benchmark = loadScenario("scenarios/explicit-ordered-sequence.hf.yaml");
      // verifySequenceOrdering(benchmark, NextSequenceType.NEW_SEQUENCE_ACTION);
      runScenario(benchmark);
   }
}
