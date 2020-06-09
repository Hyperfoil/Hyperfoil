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
      return Holder.current.get().peek();
   }

   static void push(Locator locator) {
      Holder.current.get().push(locator);
   }

   static void push(StepBuilder<?> stepBuilder) {
      Stack<Locator> stack = Holder.current.get();
      stack.push(new Impl(stepBuilder, stack.peek().sequence()));
   }

   static void pop() {
      Holder.current.get().pop();
   }

   class Holder {
      private static ThreadLocal<Stack<Locator>> current = ThreadLocal.withInitial(Stack::new);
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
