package io.sailrocket.core.machine;

public class HttpResponseState extends State {
   static final String HANDLE_STATUS = "status";
   static final String HANDLE_EXCEPTION = "exception";
   static final String HANDLE_BODY = "body";
   static final String HANDLE_END = "end";

   public HttpResponseState(String name) {
      super(name);
   }

   private void handleStatus(Session session, int status) {
      log.trace("{} Received status {}", this, status);
   }

   private void handleThrowable(Session session, Throwable throwable) {
      log.trace("{} Received exception {}", this, throwable);
   }

   private void handleBody(Session session, byte[] body) {
      log.trace("{} Received body {}", this, new String(body));
   }

   @Override
   public void register(Session session) {
      session.registerIntHandler(this, HANDLE_STATUS, status -> handleStatus(session, status));
      session.registerExceptionHandler(this, HANDLE_EXCEPTION, throwable -> handleThrowable(session, throwable));
      session.registerObjectHandler(this, HANDLE_BODY, body -> handleBody(session, (byte[]) body));
      session.registerObjectHandler(this, HANDLE_END, nil -> session.run());
   }
}
