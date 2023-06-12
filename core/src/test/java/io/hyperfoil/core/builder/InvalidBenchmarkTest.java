package io.hyperfoil.core.builder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.RelativeIteration;
import io.hyperfoil.api.config.PhaseReferenceDelay;
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

   @Test
   public void testMissingPhaseOnStartWith() {
      thrown.expectMessage(" is not defined");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startWith(new PhaseReferenceDelay("bar", RelativeIteration.NONE, null, 10)));
      builder.build();
   }

   @Test
   public void testSimultaneousStartAfterAndStartWith() {
      thrown.expectMessage("has both startWith and one of startAfter, startAfterStrict and startTime set.");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar").startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      builder.build();
   }

   @Test
   public void testSimultaneousStartAfterStrictAndStartWith() {
      thrown.expectMessage("has both startWith and one of startAfter, startAfterStrict and startTime set.");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfterStrict("bar").startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      builder.build();
   }

   @Test
   public void testSimultaneousStartTimeAndStartWith() {
      thrown.expectMessage("has both startWith and one of startAfter, startAfterStrict and startTime set.");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startTime(10).startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      builder.build();
   }

   @Test
   public void testStartWithDeadlock() {
      thrown.expectMessage("Phase dependencies contain cycle");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startWith("bar"));
      initPhase(builder.addPhase("bar").always(1).startWith("goo"));
      initPhase(builder.addPhase("goo").always(1).startWith("foo"));
      builder.build();
   }

   @Test
   public void testMixedDeadlock() {
      thrown.expectMessage("Phase dependencies contain cycle");
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      initPhase(builder.addPhase("bar").always(1).startAfterStrict("goo"));
      initPhase(builder.addPhase("goo").always(1).startWith("foo"));
      builder.build();
   }

   private void initPhase(PhaseBuilder<?> p) {
      p.duration(1).scenario().initialSequence("x").step(s -> true).endSequence().endScenario();
   }
}
