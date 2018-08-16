package io.sailrocket.core.machine;

public class ActionChain implements Action {
   private final Action[] actions;

   public ActionChain(Action... actions) {
      this.actions = actions;
   }

   @Override
   public void invoke(Session session) {
      for (int i = 0; i < actions.length; ++i) {
         actions[i].invoke(session);
      }
   }
}
