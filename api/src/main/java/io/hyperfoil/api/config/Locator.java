package io.hyperfoil.api.config;

public interface Locator {

   StepBuilder step();
   BaseSequenceBuilder sequence();
   ScenarioBuilder scenario();

   static Locator fromStep(StepBuilder step) {
      return new Step(step);
   }

   class Step implements Locator {
      private final StepBuilder step;

      public Step(StepBuilder step) {
         this.step = step;
      }

      public StepBuilder step() {
         return step;
      }

      public BaseSequenceBuilder sequence() {
         return step.endStep();
      }

      public ScenarioBuilder scenario() {
         return step.endStep().endSequence();
      }
   }
}
