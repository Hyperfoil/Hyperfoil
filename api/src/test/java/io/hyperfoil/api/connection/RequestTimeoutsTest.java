package io.hyperfoil.api.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class RequestTimeoutsTest {

   @Test
   public void testRetryDuringTimeoutDoesNotDoubleSchedule() throws InterruptedException {
      AtomicInteger scheduleCallCount = new AtomicInteger(0);
      List<Runnable> scheduledTasks = new ArrayList<>();
      TestEventExecutor executor = new TestEventExecutor(scheduleCallCount, scheduledTasks);
      RequestTimeouts timeouts = new RequestTimeouts();

      // Use a short 20ms timeout
      int timeoutMs = 20;
      long timeout = TimeUnit.MILLISECONDS.toNanos(timeoutMs);

      TestRequest req1 = new TestRequest();
      TestRequest req2 = new TestRequest(); // The retry request

      // When req1 times out, it re-entrantly schedules req2 into the same queue
      req1.onTimeoutCallback = () -> {
         timeouts.schedule(req2, executor, timeout);
      };

      // Schedule req1. This triggers the FIRST scheduleWake() -> Count = 1
      timeouts.schedule(req1, executor, timeout);
      assertEquals(1, scheduleCallCount.get(), "Initial schedule for req1");

      // Wait for req1 to expire before running the timer.
      // This ensures Queue.run() processes req1 instead of rescheduling it.
      Thread.sleep(timeoutMs * 3);

      // Execute the timer task.
      // req1 expires, fires its callback, and schedules req2 (Count = 2).
      // The loop advances to req2, which has not expired yet.
      // Checks !wakeScheduled to prevent an extra scheduleWake() call.
      scheduledTasks.get(0).run();

      assertEquals(2, scheduleCallCount.get(),
            "Expected exactly 2 schedules. If count is 3, the double-scheduling bug is present.");
   }

   private static class TestRequest extends Request {
      Runnable onTimeoutCallback;

      TestRequest() {
         super(null);
      }

      @Override
      public Void call() {
         if (onTimeoutCallback != null) {
            onTimeoutCallback.run();
         }
         return null;
      }

      @Override
      public void release() {
      }

      @Override
      public long getFiredTimestampMillis(io.hyperfoil.api.session.Session session) {
         return 0;
      }

      @Override
      public long getStartTimestampMillis(io.hyperfoil.api.session.Session session) {
         return 0;
      }

      @Override
      public long getStartTimestampNanos(io.hyperfoil.api.session.Session session) {
         return 0;
      }
   }

   private static class TestEventExecutor extends io.netty.util.concurrent.AbstractEventExecutor {
      private final AtomicInteger scheduleCount;
      private final List<Runnable> tasks;

      TestEventExecutor(AtomicInteger scheduleCount, List<Runnable> tasks) {
         super(null);
         this.scheduleCount = scheduleCount;
         this.tasks = tasks;
      }

      @Override
      public boolean inEventLoop() {
         return true;
      }

      @Override
      public boolean inEventLoop(Thread thread) {
         return true;
      }

      @Override
      public io.netty.util.concurrent.ScheduledFuture<?> schedule(Runnable command, long delay,
            TimeUnit unit) {
         scheduleCount.incrementAndGet();
         tasks.add(command);
         return null;
      }

      @Override
      public void execute(Runnable command) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void shutdown() {
      }

      @Override
      public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
         return true;
      }

      @Override
      public boolean isTerminated() {
         return false;
      }

      @Override
      public boolean isShutdown() {
         return false;
      }

      @Override
      public io.netty.util.concurrent.Future<?> terminationFuture() {
         throw new UnsupportedOperationException();
      }

      @Override
      public io.netty.util.concurrent.Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
         return null;
      }

      @Override
      public io.netty.util.concurrent.Future<?> shutdownGracefully() {
         return null;
      }

      @Override
      public boolean isShuttingDown() {
         return false;
      }
   }
}
