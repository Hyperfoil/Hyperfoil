package io.hyperfoil.api.connection;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;

/**
 * Request timeouts of one executor thread, without a scheduled task per request.
 * <p>
 * Requests with the same timeout expire in the order they were scheduled, so every distinct timeout value
 * gets a linked list of requests and a single scheduled wake-up at the deadline of its oldest request.
 * Cancelling a timeout unlinks the request from the list.
 */
final class RequestTimeouts {
   private static final Logger log = LogManager.getLogger(RequestTimeouts.class);
   private static final FastThreadLocal<RequestTimeouts> CURRENT = new FastThreadLocal<>() {
      @Override
      protected RequestTimeouts initialValue() {
         return new RequestTimeouts();
      }
   };

   private Queue[] queues = new Queue[0];

   static RequestTimeouts current() {
      return CURRENT.get();
   }

   void schedule(Request request, EventExecutor executor, long timeoutNanos) {
      assert executor.inEventLoop();
      assert request.timeoutQueue < 0;
      int index = 0;
      while (index < queues.length && queues[index].timeoutNanos != timeoutNanos) {
         ++index;
      }
      if (index == queues.length) {
         queues = Arrays.copyOf(queues, index + 1);
         queues[index] = new Queue(executor, timeoutNanos);
      }
      Queue queue = queues[index];
      assert queue.executor == executor;
      request.timeoutQueue = index;
      queue.add(request, System.nanoTime());
   }

   void cancel(Request request) {
      assert queues[request.timeoutQueue].executor.inEventLoop();
      queues[request.timeoutQueue].remove(request);
      request.timeoutQueue = -1;
   }

   private static final class Queue implements Runnable {
      final EventExecutor executor;
      final long timeoutNanos;

      // Points to the first and last requests in the chain
      private Request head;
      private Request tail;
      private boolean wakeScheduled;

      Queue(EventExecutor executor, long timeoutNanos) {
         this.executor = executor;
         this.timeoutNanos = timeoutNanos;
      }

      void add(Request request, long now) {
         request.timeoutDeadline = now + timeoutNanos;
         request.nextTimeout = null;
         request.prevTimeout = tail;

         if (tail != null) {
            tail.nextTimeout = request;
         } else {
            head = request; // Queue was empty
         }
         tail = request;

         if (!wakeScheduled) {
            scheduleWake(timeoutNanos);
         }
      }

      void remove(Request request) {
         // Unlink from the previous request
         if (request.prevTimeout != null) {
            request.prevTimeout.nextTimeout = request.nextTimeout;
         } else if (head == request) {
            head = request.nextTimeout;
         }

         // Unlink from the next request
         if (request.nextTimeout != null) {
            request.nextTimeout.prevTimeout = request.prevTimeout;
         } else if (tail == request) {
            tail = request.prevTimeout;
         }

         // Clean up the request's own pointers
         request.prevTimeout = null;
         request.nextTimeout = null;
         request.timeoutQueue = -1;
      }

      private void scheduleWake(long delayNanos) {
         wakeScheduled = true;
         executor.schedule(this, delayNanos, TimeUnit.NANOSECONDS);
      }

      @Override
      public void run() {
         wakeScheduled = false;
         long now = System.nanoTime();

         while (head != null) {
            Request request = head;
            long remaining = request.timeoutDeadline - now;

            // If the oldest request hasn't expired yet, stop and wait.
            if (remaining > 0) {
               if (!wakeScheduled) { // Prevents duplicate scheduling during re-entrant
                  scheduleWake(remaining);
               }
               return;
            }

            // Remove the expired request from the front of the chain
            head = request.nextTimeout;
            if (head != null) {
               head.prevTimeout = null;
            } else {
               tail = null; // Chain is now empty
            }

            request.prevTimeout = null;
            request.nextTimeout = null;
            request.timeoutQueue = -1;

            // Trigger the timeout logic
            try {
               request.call();
            } catch (Throwable t) {
               log.error("Timeout task failed", t);
            }
         }
      }
   }
}
