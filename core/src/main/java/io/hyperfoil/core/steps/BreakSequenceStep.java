package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.Condition;
import io.hyperfoil.core.builders.DependencyStepBuilder;
import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.function.SerializablePredicate;
import io.hyperfoil.function.SerializableSupplier;

public class BreakSequenceStep extends DependencyStep {
   private final SerializablePredicate<Session> condition;
   private final Action onBreak;

   public BreakSequenceStep(SerializableSupplier<Sequence> sequence, VarReference[] dependencies, SerializablePredicate<Session> condition, Action onBreak) {
      super(sequence, dependencies);
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

   public static class Builder extends DependencyStepBuilder {
      private Condition.Builder condition;
      private Action.Builder onBreak;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
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

      public IntCondition.Builder intCondition() {
         IntCondition.Builder builder = new IntCondition.Builder(this);
         condition(builder);
         return builder;
      }

      public Builder onBreak(Action onBreak) {
         if (this.onBreak != null) {
            throw new BenchmarkDefinitionException("Break action already set");
         }
         this.onBreak = () -> onBreak;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (condition == null) {
            throw new BenchmarkDefinitionException("In breakSequence step the condition must be defined.");
         }
         return Collections.singletonList(new BreakSequenceStep(sequence, dependencies(), condition.build(), onBreak.build()));
      }
   }

}
