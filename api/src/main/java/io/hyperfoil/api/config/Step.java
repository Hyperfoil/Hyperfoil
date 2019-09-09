package io.hyperfoil.api.config;

import java.io.Serializable;

import io.hyperfoil.api.session.Session;

public interface Step extends Serializable {
   /**
    * This method should have no side-effect if it returns false.
    *
    * @param session User session.
    * @return True if the step was successfully invoked or false when the execution is blocked.
    */
   boolean invoke(Session session);

   /**
    * Marker interface that should have single implementation in other module.
    */
   interface Catalog {}
}
