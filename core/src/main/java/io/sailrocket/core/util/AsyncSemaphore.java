package io.sailrocket.core.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AsyncSemaphore {
   private static final AtomicIntegerFieldUpdater<AsyncSemaphore> updater =
         AtomicIntegerFieldUpdater.newUpdater(AsyncSemaphore.class, "currentPermits");

   // TODO to increase throughput we could use several queues, making this unfair a bit
   private final Queue<Runnable> requestors = new ConcurrentLinkedQueue<>();
   private volatile int currentPermits;

   public AsyncSemaphore(int maxPermits) {
      currentPermits = maxPermits;
   }

   public void acquire(Runnable handler) {
      requestors.add(handler);
      for (;;) {
         int current = updater.get(this);
         if (current > 0) {
            if (updater.compareAndSet(this, current, current - 1)) {
               Runnable runningHandler = requestors.poll();
               if (runningHandler != null) {
                  // handler should invoke release
                  runningHandler.run();
               }
               return;
            }
         } else {
            break;
         }
      }
   }

   /**
    * Warning, this will run any handler that's currently scheduled!
    */
   public void release() {
      Runnable handler;
      while ((handler = requestors.poll()) != null) {
         handler.run();
      }
      int current;
      do {
         current = updater.get(this);
      } while (!updater.compareAndSet(this, current, current + 1));
   }
}
