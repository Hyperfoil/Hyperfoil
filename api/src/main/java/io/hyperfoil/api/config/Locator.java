package io.hyperfoil.api.config;

public interface Locator {

   StepBuilder<?> step();

   BaseSequenceBuilder sequence();

   ScenarioBuilder scenario();

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

   class Mutable implements Locator {
      private StepBuilder<?> step;
      private BaseSequenceBuilder sequence;
      private ScenarioBuilder scenario;

      public Mutable step(StepBuilder<?> step) {
         this.step = step;
         return this;
      }

      public Mutable sequence(BaseSequenceBuilder sequence) {
         this.sequence = sequence;
         this.scenario = sequence.endSequence();
         return this;
      }

      public Mutable scenario(ScenarioBuilder scenario) {
         this.scenario = scenario;
         return this;
      }

      @Override
      public StepBuilder<?> step() {
         if (step == null) {
            throw new IllegalStateException();
         }
         return step;
      }

      @Override
      public BaseSequenceBuilder sequence() {
         if (sequence == null) {
            throw new IllegalStateException();
         }
         return sequence;
      }

      @Override
      public ScenarioBuilder scenario() {
         if (scenario == null) {
            throw new IllegalStateException();
         }
         return scenario;
      }
   }
}
