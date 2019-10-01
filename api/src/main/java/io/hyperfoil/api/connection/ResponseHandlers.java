package io.hyperfoil.api.connection;

public interface ResponseHandlers<R extends Request> {
   void handleThrowable(R request, Throwable throwable);

   /**
    * @param request  Request.
    * @param executed True if the request was sent to the server, false if it was intercepted and cancelled.
    */
   void handleEnd(R request, boolean executed);
}
