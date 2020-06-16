package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class LoopStep implements Step, ResourceUtilizer {
   private final Access counterVar;
   private final int repeats;

   public LoopStep(Access counterVar, int repeats) {
      this.counterVar = counterVar;
      this.repeats = repeats;
   }

   @Override
   public boolean invoke(Session session) {
      // addToInt returns previous value
      int value = 1 + counterVar.addToInt(session, 1);
      if (value < repeats) {
         session.currentSequence().restart(session);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      counterVar.declareInt(session);
   }

   /**
    * Repeats a set of steps fixed number of times.
    * <p>
    * This step is supposed to be inserted as the first step of a sequence
    * and the {@code counterVar} should not be set during the first invocation.
    * <p>
    * Before the loop the {@code counterVar} is initialized to zero, and in each
    * after the last step in the {@code steps} sequence the counter is incremented.
    * If the result is lesser than {@code repeats} this sequence is restarted from
    * the beginning (this is also why the step must be the first step in the sequence).
    * <p>
    * It is legal to place steps after the looped steps.
    * <p>
    * Example:
    * <pre>
    * scenario:
    * - mySequence:
    *   - loop:
    *       counterVar: myCounter
    *       repeats: 5
    *       steps:
    *       - httpRequest:
    *           GET: /foo/${myCounter}
    *           # ...
    *       - someOtherStep: ...
    *   - anotherStepExecutedAfterThoseFiveLoops
    * </pre>
    */
   @MetaInfServices(StepBuilder.class)
   @Name("loop")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String counterVar;
      private int repeats;
      private final LoopStepsBuilder steps;

      public Builder() {
         this(null);
      }

      public Builder(BaseSequenceBuilder parent) {
         steps = new LoopStepsBuilder(parent);
         if (parent != null) {
            addTo(parent);
         }
      }

      /**
       * @param counterVar Variable holding number of iterations.
       * @return Self.
       */
      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      /**
       * @param repeats Number of iterations that should be executed.
       * @return Self.
       */
      public Builder repeats(int repeats) {
         this.repeats = repeats;
         return this;
      }

      /**
       * @param sequence Name of the sequence that should be instantiated.
       * @return Self.
       */
      @Deprecated
      public Builder sequence(String sequence) {
         throw new BenchmarkDefinitionException("Sequence is not supported anymore; place loop as the first step in a sequence.");
      }

      public LoopStepsBuilder steps() {
         return steps;
      }

      @Override
      public void prepareBuild() {
         steps.prepareBuild();
      }

      @Override
      public List<Step> build() {
         Locator locator = Locator.current();
         if (locator.sequence().indexOf(this) != 0) {
            throw new BenchmarkDefinitionException("Loop step must be placed as the first step in a sequence.");
         } else if (counterVar == null) {
            throw new BenchmarkDefinitionException("loop.counterVar must be set.");
         } else if (repeats <= 0) {
            throw new BenchmarkDefinitionException("loop.repeats must be > 0");
         } else if (steps.isEmpty()) {
            throw new BenchmarkDefinitionException("The loop does not include any steps. Cannot construct empty loop.");
         }
         Access counter = SessionFactory.access(counterVar);
         if (locator.sequence().rootSequence().concurrency() > 0 && !counter.isSequenceScoped()) {
            throw new BenchmarkDefinitionException("In concurrent sequences the counter var should be sequence-scoped.");
         }
         ArrayList<Step> allSteps = new ArrayList<>();
         allSteps.add(new ActionStep(new SetIntAction(counter, 0, true, null)));
         allSteps.addAll(steps.buildSteps());
         allSteps.add(new LoopStep(counter, repeats));
         return allSteps;
      }

      public class LoopStepsBuilder extends BaseSequenceBuilder {
         public LoopStepsBuilder(BaseSequenceBuilder parent) {
            super(parent);
         }
      }
   }
}
