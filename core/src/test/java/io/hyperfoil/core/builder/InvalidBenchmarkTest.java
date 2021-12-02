package io.hyperfoil.core.builder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.core.builders.StepCatalog;

public class InvalidBenchmarkTest {
   @SuppressWarnings("deprecation")
   @Rule
   public ExpectedException thrown = ExpectedException.none();

   @Test
   public void testMissingPhase() {
      thrown.expectMessage(" is not defined");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      builder.build();
   }

   @Test
   public void testDeadlock() {
      thrown.expectMessage("Phase dependencies contain cycle");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      initPhase(builder.addPhase("bar").always(1).startAfterStrict("goo"));
      initPhase(builder.addPhase("goo").always(1).startAfter("foo"));
      builder.build();
   }

   @Test
   public void testVariableNotWritten() {
      thrown.expectMessage("Variable 'foo' is read but it is never written to");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      builder.addPhase("test").atOnce(1).scenario().initialSequence("test")
            .step(StepCatalog.SC).log("Blabla: ${foo}");
      builder.build();
   }

   private void initPhase(PhaseBuilder<?> p) {
      p.duration(1).scenario().initialSequence("x").step(s -> true).endSequence().endScenario();
   }
}
