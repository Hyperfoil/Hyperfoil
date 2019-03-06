package io.hyperfoil.api.connection;

public interface ResponseHandlers<R extends Request> {
   void handleThrowable(R request, Throwable throwable);

   void handleEnd(R request);
}
