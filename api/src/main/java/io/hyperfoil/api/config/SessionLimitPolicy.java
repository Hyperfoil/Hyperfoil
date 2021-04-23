package io.hyperfoil.api.config;

public enum SessionLimitPolicy {
   /**
    * Cancel all sessions that did not start yet if the session limit is reached.
    */
   FAIL,
   /**
    * Continue even if we've reached maximum sessions.
    */
   CONTINUE
}
