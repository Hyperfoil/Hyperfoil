package io.sailrocket.core.machine;

public interface Action {
   /**
    * @return false if the action cannot be invoked and the progress must be blocked.
    */
   default boolean prepare(Session session) {
      return true;
   }

   void invoke(Session session);
}
