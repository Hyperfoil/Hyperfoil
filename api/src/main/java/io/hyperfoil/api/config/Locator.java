package io.hyperfoil.api.config;

public interface Locator {

   StepBuilder<?> step();

   BaseSequenceBuilder sequence();

   ScenarioBuilder scenario();

   default BenchmarkBuilder benchmark() {
      return scenario().endScenario().endPhase();
   }

   static Locator get(StepBuilder<?> step, Locator locator) {
      return new Impl(step, locator.sequence());
   }

   class Impl implements Locator {
      private final StepBuilder<?> step;
      private final BaseSequenceBuilder sequence;

      private Impl(StepBuilder<?> step, BaseSequenceBuilder sequence) {
         this.step = step;
         this.sequence = sequence;
      }

      public StepBuilder<?> step() {
         return step;
      }

      public BaseSequenceBuilder sequence() {
         return sequence;
      }

      public ScenarioBuilder scenario() {
         return sequence.endSequence();
      }
   }
}
