package io.sailrocket.api.connection;

public interface ResponseHandlers {
   void handleThrowable(Request request, Throwable throwable);

   void handleEnd(Request request);
}
