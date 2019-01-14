package io.sailrocket.api.connection;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.netty.util.concurrent.ScheduledFuture;
import io.sailrocket.api.session.SequenceInstance;
import io.sailrocket.api.session.Session;

public class Request implements Callable<Void> {
   private static final TimeoutException TIMEOUT_EXCEPTION = new TimeoutException();

   public final Session session;
   private long startTime;
   private SequenceInstance sequence;
   private ScheduledFuture<?> timeoutFuture;
   private Object requestData;
   private ResponseHandlers handlers;
   private Connection connection;
   private boolean completed = true;

   public Request(Session session) {
      this.session = session;
   }

   /**
    * This method works as timeout handler
    */
   @Override
   public Void call() {
      timeoutFuture = null;
      if (!isCompleted()) {
         sequence.statistics(session).incrementTimeouts();
         handlers().handleThrowable(this, TIMEOUT_EXCEPTION);
         // handleThrowable sets the request completed
      }
      return null;
   }

   public void start(ResponseHandlers handlers, SequenceInstance sequence) {
      this.startTime = System.nanoTime();
      this.handlers = handlers;
      this.sequence = sequence;
      this.completed = false;
   }

   public Object requestData() {
      return requestData;
   }

   public void setRequestData(Object requestData) {
      this.requestData = requestData;
   }

   public void attach(Connection connection) {
      this.connection = connection;
   }

   public boolean isCompleted() {
      return completed;
   }

   public void setCompleted() {
      if (timeoutFuture != null) {
         timeoutFuture.cancel(false);
         timeoutFuture = null;
      }
      connection = null;
      completed = true;
   }

   public Connection connection() {
      return connection;
   }

   public ResponseHandlers handlers() {
      return handlers;
   }

   public SequenceInstance sequence() {
      return sequence;
   }

   public long startTime() {
      return startTime;
   }

   public void setTimeout(long timeout, TimeUnit timeUnit) {
      timeoutFuture = session.executor().schedule(this, timeout, timeUnit);
   }

}
