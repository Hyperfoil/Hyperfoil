package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.builders.Condition;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.function.SerializablePredicate;

public class BreakSequenceStep extends DependencyStep {
   private final SerializablePredicate<Session> condition;
   private final Action[] onBreak;

   public BreakSequenceStep(Access[] dependencies, SerializablePredicate<Session> condition, Action[] onBreak) {
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
            for (Action a : onBreak) {
               a.run(session);
            }
         }
         session.currentSequence().breakSequence(session);
      }
      return true;
   }

   /**
    * Prematurely stops execution of this sequence if the condition is satisfied.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("breakSequence")
   public static class Builder extends DependencyStepBuilder<Builder> {
      private final List<Action.Builder> onBreak = new ArrayList<>();
      private Condition.TypesBuilder<Builder> condition = new Condition.TypesBuilder<>(this);

      @Embed
      public Condition.TypesBuilder<Builder> condition() {
         return condition;
      }

      public Builder onBreak(Action onBreak) {
         return onBreak(() -> onBreak);
      }

      public Builder onBreak(Action.Builder onBreak) {
         this.onBreak.add(onBreak);
         return this;
      }

      /**
       * Action performed when the condition is true and the sequence is to be ended.
       *
       * @return Service-loaded action builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> onBreak() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, this::onBreak);
      }

      @Override
      public List<Step> build() {
         Condition condition = this.condition.buildCondition();
         if (condition == null) {
            throw new BenchmarkDefinitionException("In breakSequence step the condition must be defined.");
         }
         Action[] onBreak = this.onBreak.isEmpty() ? null : this.onBreak.stream().map(Action.Builder::build).toArray(Action[]::new);
         return Collections.singletonList(new BreakSequenceStep(dependencies(), condition, onBreak));
      }
   }

}
