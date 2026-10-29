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
 * gets a FIFO of requests and a single scheduled wake-up at the deadline of its oldest request. Cancelling
 * a timeout clears the request's slot in the FIFO; the head of the FIFO skips cleared slots. A request only
 * records primitive fields (queue index, slot, deadline), so scheduling and cancelling a timeout do not store
 * references into the long-lived request objects.
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
      // Ring buffer with a power-of-two capacity: slots head .. head + length - 1 are in use,
      // the head slot always holds a request unless the queue is empty.
      private Request[] slots = new Request[16];
      private int head;
      private int length;
      private int size;
      private boolean wakeScheduled;

      Queue(EventExecutor executor, long timeoutNanos) {
         this.executor = executor;
         this.timeoutNanos = timeoutNanos;
      }

      void add(Request request, long now) {
         if (length == slots.length) {
            compact();
         }
         int slot = (head + length) & (slots.length - 1);
         slots[slot] = request;
         request.timeoutSlot = slot;
         request.timeoutDeadline = now + timeoutNanos;
         ++length;
         ++size;
         if (!wakeScheduled) {
            scheduleWake(timeoutNanos);
         }
      }

      void remove(Request request) {
         int slot = request.timeoutSlot;
         assert slots[slot] == request;
         slots[slot] = null;
         request.timeoutSlot = -1;
         --size;
         if (slot == head) {
            skipCleared();
         }
      }

      private void skipCleared() {
         int mask = slots.length - 1;
         while (length > 0 && slots[head] == null) {
            head = (head + 1) & mask;
            --length;
         }
      }

      private void compact() {
         Request[] compacted = new Request[size * 2 > slots.length ? slots.length * 2 : slots.length];
         int mask = slots.length - 1;
         int copied = 0;
         for (int i = 0; i < length; ++i) {
            Request request = slots[(head + i) & mask];
            if (request != null) {
               compacted[copied] = request;
               request.timeoutSlot = copied;
               ++copied;
            }
         }
         assert copied == size;
         slots = compacted;
         head = 0;
         length = copied;
      }

      private void scheduleWake(long delayNanos) {
         wakeScheduled = true;
         executor.schedule(this, delayNanos, TimeUnit.NANOSECONDS);
      }

      @Override
      public void run() {
         wakeScheduled = false;
         long now = System.nanoTime();
         int mask = slots.length - 1;
         while (length > 0) {
            Request request = slots[head];
            if (request == null) {
               head = (head + 1) & mask;
               --length;
               continue;
            }
            long remaining = request.timeoutDeadline - now;
            if (remaining > 0) {
               scheduleWake(remaining);
               return;
            }
            slots[head] = null;
            head = (head + 1) & mask;
            --length;
            --size;
            request.timeoutSlot = -1;
            request.timeoutQueue = -1;
            try {
               request.call();
            } catch (Throwable t) {
               log.error("Timeout task failed", t);
            }
         }
      }
   }
}
