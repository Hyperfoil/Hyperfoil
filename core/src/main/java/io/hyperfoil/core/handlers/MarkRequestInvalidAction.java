package io.hyperfoil.core.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;

public class MarkRequestInvalidAction implements Action {
   private static final Logger log = LogManager.getLogger(MarkRequestInvalidAction.class);

   @Override
   public void run(Session session) {
      Request request = session.currentRequest();
      if (request == null) {
         log.error("#{} No request in progress", session.uniqueId());
      } else {
         log.trace("#{} Marking request as invalid", session.uniqueId());
         request.markInvalid();
      }
   }

   /**
    * Unconditionally mark currently processed request as invalid.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("markRequestInvalid")
   public static class Builder implements Action.Builder {
      @Override
      public Action build() {
         return new MarkRequestInvalidAction();
      }
   }
}
