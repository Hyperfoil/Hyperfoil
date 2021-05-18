package io.hyperfoil.api.session;

public interface ObjectAccess extends WriteAccess {
   void setObject(Session session, Object value);

   /**
    * Make variable set without changing its (pre-allocated) value.
    *
    * @param session Session with variables.
    * @return Variable value
    */
   Object activate(Session session);
}
