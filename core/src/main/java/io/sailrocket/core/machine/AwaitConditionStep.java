package io.sailrocket.core.machine;

import java.util.function.Predicate;

public class AwaitConditionStep implements Step {
   private final Predicate<Session> condition;

   public AwaitConditionStep(Predicate<Session> condition) {
      this.condition = condition;
   }

   @Override
   public boolean prepare(Session session) {
      return condition.test(session);
   }

   @Override
   public void invoke(Session session) {
      // noop
   }
}
