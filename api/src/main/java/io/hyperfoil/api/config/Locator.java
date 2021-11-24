package io.hyperfoil.api.config;

import java.util.Stack;

public interface Locator {

   StepBuilder<?> step();

   BaseSequenceBuilder<?> sequence();

   ScenarioBuilder scenario();

   default BenchmarkBuilder benchmark() {
      return scenario().endScenario().endPhase();
   }

   String locationMessage();

   static Locator current() {
      Stack<Locator> stack = Holder.CURRENT.get();
      if (stack.isEmpty()) {
         throw new IllegalArgumentException("Locator is not set. This method must be invoked within the prepareBuild() or build() phase of scenario.");
      }
      return stack.peek();
   }

   static boolean isAvailable() {
      Stack<Locator> stack = Holder.CURRENT.get();
      return !stack.isEmpty();
   }

   static void push(Locator locator) {
      Holder.CURRENT.get().push(locator);
   }

   static void push(StepBuilder<?> stepBuilder, BaseSequenceBuilder<?> sequenceBuilder) {
      Stack<Locator> stack = Holder.CURRENT.get();
      stack.push(new Impl(stepBuilder, sequenceBuilder, sequenceBuilder.endSequence()));
   }

   static void push(ScenarioBuilder scenarioBuilder) {
      Stack<Locator> stack = Holder.CURRENT.get();
      stack.push(new Impl(null, null, scenarioBuilder));
   }

   static void pop() {
      Holder.CURRENT.get().pop();
   }

   class Holder {
      private static final ThreadLocal<Stack<Locator>> CURRENT = ThreadLocal.withInitial(Stack::new);
   }

   class Impl implements Locator {
      private final StepBuilder<?> step;
      private final BaseSequenceBuilder<?> sequence;
      private final ScenarioBuilder scenario;

      private Impl(StepBuilder<?> step, BaseSequenceBuilder<?> sequence, ScenarioBuilder scenario) {
         this.step = step;
         this.sequence = sequence;
         this.scenario = scenario;
      }

      public StepBuilder<?> step() {
         return step;
      }

      public BaseSequenceBuilder<?> sequence() {
         return sequence;
      }

      public ScenarioBuilder scenario() {
         return scenario;
      }

      @Override
      public String locationMessage() {
         StringBuilder sb = new StringBuilder("Phase ").append(scenario().endScenario().name);
         String forkName = scenario().fork().name;
         if (forkName != null) {
            sb.append("/").append(forkName);
         }
         if (sequence != null) {
            sb.append(", sequence ").append(sequence.name());
         }
         if (step != null) {
            sb.append(", step ");
            sb.append(StepBuilder.nameOf(step));
            int stepIndex = sequence.indexOf(step);
            if (stepIndex >= 0) {
               sb.append(" (").append(stepIndex).append("/").append(sequence.size()).append(")");
            }
         }
         return sb.toString();
      }

   }
}
