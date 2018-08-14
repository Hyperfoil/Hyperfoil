package io.sailrocket.core.machine;

import java.util.function.Predicate;

class Transition {
   final Predicate<Session> condition;
   final Action action;
   final boolean blocking;

   Transition(Predicate<Session> condition, Action action, boolean blocking) {
      this.condition = condition;
      this.action = action;
      this.blocking = blocking;
   }

   boolean test(Session session) {
      return condition == null || condition.test(session);
   }

   State invoke(Session session) {
      return action.invoke(session);
   }
}
