package io.sailrocket.core.machine;

import java.util.function.IntPredicate;

public class AwaitIntConditionAction implements Action {
   private final String var;
   private final IntPredicate condition;

   public AwaitIntConditionAction(String var, IntPredicate condition) {
      this.var = var;
      this.condition = condition;
   }

   @Override
   public boolean prepare(Session session) {
      return condition.test(session.getInt(var));
   }

   @Override
   public void invoke(Session session) {
      // noop
   }
}
