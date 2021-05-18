package io.hyperfoil.api.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public interface WriteAccess extends ReadAccess {
   Logger log = LogManager.getLogger(WriteAccess.class);
   boolean trace = log.isTraceEnabled();

   void unset(Session session);

   Session.Var createVar(Session session, Session.Var existing);
}
