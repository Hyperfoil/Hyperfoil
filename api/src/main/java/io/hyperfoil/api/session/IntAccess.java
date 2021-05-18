package io.hyperfoil.api.session;

public interface IntAccess extends WriteAccess {
   void setInt(Session session, int value);

   int addToInt(Session session, int delta);

   @Override
   Session.Var getVar(Session session);
}
