package io.hyperfoil.api.config;

import java.util.Stack;

public interface Locator {
   StepBuilder<?> step();

   BaseSequenceBuilder sequence();

   ScenarioBuilder scenario();

   default BenchmarkBuilder benchmark() {
      return scenario().endScenario().endPhase();
   }

   static Locator current() {
      Stack<Locator> stack = Holder.CURRENT.get();
      if (stack.isEmpty()) {
         throw new IllegalArgumentException("Locator is not set. This method must be invoked within the prepareBuild() or build() phase of scenario.");
      }
      return stack.peek();
   }

   static void push(Locator locator) {
      Holder.CURRENT.get().push(locator);
   }

   static void push(StepBuilder<?> stepBuilder) {
      Stack<Locator> stack = Holder.CURRENT.get();
      stack.push(new Impl(stepBuilder, stack.peek().sequence()));
   }

   static void pop() {
      Holder.CURRENT.get().pop();
   }

   static Locator forTesting() {
      return Holder.TESTING_MOCK;
   }

   class Holder {
      private static final ThreadLocal<Stack<Locator>> CURRENT = ThreadLocal.withInitial(Stack::new);
      private static final Locator TESTING_MOCK = new Locator() {
         @Override
         public StepBuilder<?> step() {
            throw new UnsupportedOperationException();
         }

         @Override
         public BaseSequenceBuilder sequence() {
            throw new UnsupportedOperationException();
         }

         @Override
         public ScenarioBuilder scenario() {
            throw new UnsupportedOperationException();
         }
      };
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
