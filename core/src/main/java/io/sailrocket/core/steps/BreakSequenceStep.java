package io.sailrocket.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.sailrocket.api.Session;
import io.sailrocket.api.Step;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;

public class BreakSequenceStep extends BaseStep {
   private final Predicate<Session> condition;
   private final Consumer<Session> onBreak;

   public BreakSequenceStep(Predicate<Session> condition, Consumer<Session> onBreak) {
      this.condition = condition;
      this.onBreak = onBreak;
   }

   @Override
   public void invoke(Session session) {
      if (condition.test(session)) {
         if (onBreak != null) {
            onBreak.accept(session);
         }
         session.currentSequence(null);
      }
   }

   public static class Builder extends BaseStepBuilder {
      private final Predicate<Session> condition;
      private Consumer<Session> onBreak;

      public Builder(BaseSequenceBuilder parent, Predicate<Session> condition) {
         super(parent);
         this.condition = condition;
      }

      public BaseStepBuilder onBreak(Consumer<Session> onBreak) {
         this.onBreak = onBreak;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new BreakSequenceStep(condition, onBreak));
      }
   }
}
