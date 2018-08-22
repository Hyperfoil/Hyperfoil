package io.sailrocket.core.machine;

public class ActionChain implements Action, ResourceUtilizer {
   private final Action[] actions;

   public ActionChain(Action... actions) {
      this.actions = actions;
   }

   @Override
   public boolean prepare(Session session) {
      if (!session.isSet(this)) {
         session.setInt(this, 0);
      }
      for (int i = session.getInt(this); i < actions.length; ++i) {
         if (!actions[i].prepare(session)) {
            session.setInt(this, i);
            return false;
         }
      }
      session.setInt(this, 0);
      return true;
   }

   @Override
   public void invoke(Session session) {
      for (Action action : actions) {
         action.invoke(session);
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(this);
      for (Action action : actions) {
         if (action instanceof ResourceUtilizer) {
            ((ResourceUtilizer) action).reserve(session);
         }
      }
   }
}
