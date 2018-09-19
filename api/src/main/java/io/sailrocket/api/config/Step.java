package io.sailrocket.api.config;

import java.io.Serializable;

import io.sailrocket.api.session.Session;

public interface Step extends Serializable {
   /**
    * @return false if the step cannot be invoked and the progress must be blocked.
    */
   default boolean prepare(Session session) {
      return true;
   }

   void invoke(Session session);
}
