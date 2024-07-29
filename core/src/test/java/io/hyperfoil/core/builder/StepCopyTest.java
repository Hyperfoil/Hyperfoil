package io.hyperfoil.core.builder;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.steps.NoopStep;

public class StepCopyTest {
   @Test
   public void testLoop() {
      AtomicInteger invoked = new AtomicInteger();
      test(seq -> seq.step(StepCatalog.SC).loop("foo", 2).steps()
            .step(new NoopStep())
            .stepBuilder(new TestStepBuilder(invoked)).endSequence());
      assert invoked.get() == 2;
   }

   @Test
   public void testStopwatch() {
      test(seq -> seq.step(StepCatalog.SC).stopwatch()
            .step(new NoopStep()).step(new NoopStep()));
   }

   private void test(Consumer<SequenceBuilder> consumer) {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder();
      ScenarioBuilder scenario = benchmarkBuilder.addPhase("test").atOnce(1).users(1).duration(1).scenario();
      SequenceBuilder first = scenario.initialSequence("first");
      consumer.accept(first);
      scenario.sequence("second", first);
      benchmarkBuilder.build();
   }

   public static class TestStepBuilder implements StepBuilder {
      private final AtomicInteger invoked;

      public TestStepBuilder(AtomicInteger invoked) {
         this.invoked = invoked;
      }

      public TestStepBuilder(TestStepBuilder other) {
         this.invoked = other.invoked;
      }

      @Override
      public List<Step> build() {
         Locator locator = Locator.current();
         assert locator.step() == this;
         int counter = invoked.incrementAndGet();
         assert (counter == 1 ? "first" : "second").equals(locator.sequence().name());
         assert locator.scenario() != null;
         assert locator.benchmark() != null;
         return Collections.singletonList(new NoopStep());
      }
   }
}
