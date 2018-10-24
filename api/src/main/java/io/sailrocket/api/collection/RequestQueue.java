package io.sailrocket.api.collection;

import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import io.netty.util.concurrent.ScheduledFuture;
import io.sailrocket.api.session.SequenceInstance;
import io.sailrocket.api.session.Session;

public interface RequestQueue {
   Request prepare();

   Request peek();

   Request complete();

   /**
    * @return true if {@link #prepare()} would return <code>null</code>.
    */
   boolean isDepleted();

   /**
    * @return true if all requests are in the queue (there are no in-flight requests)
    */
   boolean isFull();

   void gc();

   Session session();

   class Request implements Runnable {
      private static final TimeoutException TIMEOUT_EXCEPTION = new TimeoutException();

      public final RequestQueue queue;
      public long startTime;
      public SequenceInstance sequence;
      public ScheduledFuture<?> timeoutFuture;
      public Consumer<Throwable> exceptionHandler;
      public io.sailrocket.api.connection.Request request;

      public Request(RequestQueue queue) {
         this.queue = queue;
      }

      /**
       * This method works as timeout handler
       */
      @Override
      public void run() {
         timeoutFuture = null;
         if (!request.isCompleted()) {
            sequence.statistics(queue.session()).incrementTimeouts();
            exceptionHandler.accept(TIMEOUT_EXCEPTION);
            request.setCompleted();
         }
         queue.gc();
      }
   }
}
