package io.hyperfoil.core.builder;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.PhaseReferenceDelay;
import io.hyperfoil.api.config.RelativeIteration;
import io.hyperfoil.core.builders.StepCatalog;

public class InvalidBenchmarkTest {

   @Test
   public void testMissingPhase() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, " is not defined");
   }

   @Test
   public void testDeadlock() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      initPhase(builder.addPhase("bar").always(1).startAfterStrict("goo"));
      initPhase(builder.addPhase("goo").always(1).startAfter("foo"));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "Phase dependencies contain cycle");
   }

   @Test
   public void testVariableNotWritten() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      builder.addPhase("test").atOnce(1).scenario().initialSequence("test")
            .step(StepCatalog.SC).log("Blabla: ${foo}");
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "Variable 'foo' is read but it is never written to");
   }

   @Test
   public void testMissingPhaseOnStartWith() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startWith(new PhaseReferenceDelay("bar", RelativeIteration.NONE, null, 10)));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, " is not defined");
   }

   @Test
   public void testSimultaneousStartAfterAndStartWith() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar").startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "has both startWith and one of startAfter, startAfterStrict and startTime set.");
   }

   @Test
   public void testSimultaneousStartAfterStrictAndStartWith() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfterStrict("bar").startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "has both startWith and one of startAfter, startAfterStrict and startTime set.");
   }

   @Test
   public void testSimultaneousStartTimeAndStartWith() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startTime(10).startWith("bar"));
      initPhase(builder.addPhase("bar").always(1));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "has both startWith and one of startAfter, startAfterStrict and startTime set.");
   }

   @Test
   public void testStartWithDeadlock() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startWith("bar"));
      initPhase(builder.addPhase("bar").always(1).startWith("goo"));
      initPhase(builder.addPhase("goo").always(1).startWith("foo"));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "Phase dependencies contain cycle");
   }

   @Test
   public void testMixedDeadlock() {
      BenchmarkBuilder builder = BenchmarkBuilder.builder();
      initPhase(builder.addPhase("foo").always(1).startAfter("bar"));
      initPhase(builder.addPhase("bar").always(1).startAfterStrict("goo"));
      initPhase(builder.addPhase("goo").always(1).startWith("foo"));
      Exception thrown = assertThrows(BenchmarkDefinitionException.class, builder::build);
      assertExceptionMessageContains(thrown, "Phase dependencies contain cycle");
   }

   private void initPhase(PhaseBuilder<?> p) {
      p.duration(1).scenario().initialSequence("x").step(s -> true).endSequence().endScenario();
   }

   private void assertExceptionMessageContains(Exception exception, String expectedMessage) {
      if (!exception.getMessage().contains(expectedMessage)) {
         throw new AssertionError(
               "Expected message to contain \"" + expectedMessage + "\" but was \"" + exception.getMessage() + "\"");
      }
   }
}
