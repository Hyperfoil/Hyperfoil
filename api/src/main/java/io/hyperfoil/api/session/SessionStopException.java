package io.hyperfoil.api.session;

/**
 * This exception is raised to prevent any further processing after the session is stopped.
 */
public class SessionStopException extends RuntimeException {
   public static final SessionStopException INSTANCE = new SessionStopException();

   private SessionStopException() {
      super(null, null, false, false);
   }
}
