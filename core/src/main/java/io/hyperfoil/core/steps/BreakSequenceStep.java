package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.builders.Condition;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.function.SerializablePredicate;

public class BreakSequenceStep extends DependencyStep {
   private final SerializablePredicate<Session> condition;
   private final Action onBreak;

   public BreakSequenceStep(Access[] dependencies, SerializablePredicate<Session> condition, Action onBreak) {
      super(dependencies);
      this.condition = condition;
      this.onBreak = onBreak;
   }

   @Override
   public boolean invoke(Session session) {
      if (!super.invoke(session)) {
         return false;
      }
      if (condition.test(session)) {
         if (onBreak != null) {
            onBreak.run(session);
         }
         session.currentSequence(null);
      }
      return true;
   }

   /**
    * Prematurely stops execution of this sequence if the condition is satisfied.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("breakSequence")
   public static class Builder extends DependencyStepBuilder<Builder> {
      private Locator locator;
      private Condition.Builder condition;
      private Action.Builder onBreak;

      @Override
      public Builder setLocator(Locator locator) {
         this.locator = locator;
         return this;
      }

      @Override
      public Builder copy(Locator locator) {
         return new Builder().setLocator(locator).condition(condition).onBreak(onBreak);
      }

      public Builder condition(Condition.Builder condition) {
         if (this.condition != null) {
            throw new BenchmarkDefinitionException("Condition already set.");
         }
         this.condition = condition;
         return this;
      }

      public Builder condition(SerializablePredicate<Session> condition) {
         return condition(() -> condition);
      }

      /**
       * Action performed when the condition is true and the sequence is to be ended.
       *
       * @return Builder.
       */
      public IntCondition.Builder<BreakSequenceStep.Builder> intCondition() {
         IntCondition.Builder<BreakSequenceStep.Builder> builder = new IntCondition.Builder<>(this);
         condition(builder);
         return builder;
      }

      public Builder onBreak(Action onBreak) {
         return onBreak(() -> onBreak);
      }

      public Builder onBreak(Action.Builder onBreak) {
         if (this.onBreak != null) {
            throw new BenchmarkDefinitionException("Break action already set");
         }
         this.onBreak = onBreak;
         return this;
      }

      /**
       * Action performed when the condition is true and the sequence is to be ended.
       *
       * @return Service-loaded action builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onBreak() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, locator, this::onBreak);
      }

      @Override
      public List<Step> build() {
         if (condition == null) {
            throw new BenchmarkDefinitionException("In breakSequence step the condition must be defined.");
         }
         return Collections.singletonList(new BreakSequenceStep(dependencies(), condition.build(), onBreak != null ? onBreak.build() : null));
      }
   }

}
