package io.sailrocket.api;

public interface Step {
   /**
    * @return false if the step cannot be invoked and the progress must be blocked.
    */
   default boolean prepare(Session session) {
      return true;
   }

   void invoke(Session session);
}
