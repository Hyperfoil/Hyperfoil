package io.sailrocket.core.machine;

public class HttpResponseState extends State {
   static final String HANDLE_STATUS = "status";
   static final String HANDLE_EXCEPTION = "exception";
   static final String HANDLE_BODY = "body";
   static final String HANDLE_END = "end";

   private void handleStatus(Session session, int status) {
      // TODO: Customise this generated block
   }

   private void handleThrowable(Session session, Throwable throwable) {

   }

   @Override
   public void register(Session session) {
      session.registerIntHandler(this, HANDLE_STATUS, status -> handleStatus(session, status));
      session.registerExceptionHandler(this, HANDLE_EXCEPTION, throwable -> handleThrowable(session, throwable));
      session.registerObjectHandler(this, HANDLE_END, nil -> session.run());
   }
}
