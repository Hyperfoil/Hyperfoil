package io.hyperfoil.api.connection;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.FastThreadLocal;

/**
 * Pending timeouts of the requests with the same timeout value on one executor thread.
 * <p>
 * These requests expire in the order they were scheduled, so they are kept in a list linked through
 * the requests themselves, and a single task wakes up at the deadline of the oldest one: no task is
 * scheduled per request. A request that completes (in any order) is unlinked in constant time.
 * <p>
 * Nothing here is thread-safe: the list must only be touched from its executor's thread, which is the
 * thread of the sessions owning the requests.
 */
final class RequestTimeouts implements Runnable {
   private static final Logger log = LogManager.getLogger(RequestTimeouts.class);
   private static final FastThreadLocal<RequestTimeouts[]> PER_THREAD = new FastThreadLocal<>() {
      @Override
      protected RequestTimeouts[] initialValue() {
         return new RequestTimeouts[0];
      }
   };

   private final EventExecutor executor;
   private final long timeoutNanos;
   private Request head;
   private Request tail;
   // Stays set while run() expires requests: Request.call() can schedule new timeouts (e.g. the session
   // continues after its connection was closed) and these must not schedule a second wake-up.
   private boolean wakeUpScheduled;

   RequestTimeouts(EventExecutor executor, long timeoutNanos) {
      this.executor = executor;
      this.timeoutNanos = timeoutNanos;
   }

   static void schedule(Request request, EventExecutor executor, long timeoutNanos) {
      assert executor.inEventLoop();
      RequestTimeouts[] lists = PER_THREAD.get();
      for (RequestTimeouts list : lists) {
         if (list.timeoutNanos == timeoutNanos) {
            assert list.executor == executor;
            list.add(request);
            return;
         }
      }
      RequestTimeouts list = new RequestTimeouts(executor, timeoutNanos);
      lists = Arrays.copyOf(lists, lists.length + 1);
      lists[lists.length - 1] = list;
      PER_THREAD.set(lists);
      list.add(request);
   }

   void add(Request request) {
      assert executor.inEventLoop();
      assert request.timeouts == null;
      request.timeouts = this;
      request.timeoutDeadline = System.nanoTime() + timeoutNanos;
      request.prevTimeout = tail;
      if (tail == null) {
         head = request;
      } else {
         tail.nextTimeout = request;
      }
      tail = request;
      if (!wakeUpScheduled) {
         wakeUpScheduled = true;
         executor.schedule(this, timeoutNanos, TimeUnit.NANOSECONDS);
      }
   }

   void remove(Request request) {
      assert executor.inEventLoop() : "request timeout cancelled outside of its event loop";
      Request prev = request.prevTimeout;
      Request next = request.nextTimeout;
      if (prev == null) {
         head = next;
      } else {
         prev.nextTimeout = next;
      }
      if (next == null) {
         tail = prev;
      } else {
         next.prevTimeout = prev;
      }
      request.prevTimeout = null;
      request.nextTimeout = null;
      request.timeouts = null;
   }

   @Override
   public void run() {
      assert executor.inEventLoop();
      long now = System.nanoTime();
      Request request;
      while ((request = head) != null && request.timeoutDeadline - now <= 0) {
         remove(request);
         try {
            request.call();
         } catch (Throwable t) {
            log.error("Timeout task failed", t);
         }
      }
      if (head == null) {
         wakeUpScheduled = false;
      } else {
         executor.schedule(this, head.timeoutDeadline - System.nanoTime(), TimeUnit.NANOSECONDS);
      }
   }
}
