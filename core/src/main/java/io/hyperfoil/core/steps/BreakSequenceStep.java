package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.DependencyStepBuilder;

public class BreakSequenceStep extends DependencyStep {
   private final Predicate<Session> condition;
   private final Consumer<Session> onBreak;

   public BreakSequenceStep(VarReference[] dependencies, Predicate<Session> condition, Consumer<Session> onBreak) {
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
            onBreak.accept(session);
         }
         session.currentSequence(null);
      }
      return true;
   }

   public static class Builder extends DependencyStepBuilder {
      private final Predicate<Session> condition;
      private Consumer<Session> onBreak;

      public Builder(BaseSequenceBuilder parent, Predicate<Session> condition) {
         super(parent);
         this.condition = condition;
      }

      public DependencyStepBuilder onBreak(Consumer<Session> onBreak) {
         this.onBreak = onBreak;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new BreakSequenceStep(dependencies(), condition, onBreak));
      }
   }
}
