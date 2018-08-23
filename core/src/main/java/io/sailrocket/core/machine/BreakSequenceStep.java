package io.sailrocket.core.machine;

import java.util.function.Consumer;
import java.util.function.Predicate;

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
         onBreak.accept(session);
         session.currentSequence(null);
      }
   }
}
