package io.hyperfoil.api.connection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.EventExecutor;

public class RequestTimeoutsTest {
   private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(10);

   // Delays of the wake-ups scheduled on the executor; the wake-up task is always the RequestTimeouts instance
   private final List<Long> wakeUps = new ArrayList<>();
   private boolean inEventLoop = true;
   private final EventExecutor executor = (EventExecutor) Proxy.newProxyInstance(getClass().getClassLoader(),
         new Class<?>[] { EventExecutor.class }, (proxy, method, args) -> switch (method.getName()) {
            case "inEventLoop" -> inEventLoop;
            case "schedule" -> {
               wakeUps.add(((TimeUnit) args[2]).toNanos((Long) args[1]));
               yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
         });
   private final RequestTimeouts timeouts = new RequestTimeouts(executor, TIMEOUT);

   @Test
   public void testTimeoutScheduledFromExpiringRequest() {
      TestRequest a = new TestRequest();
      TestRequest b = new TestRequest();
      TestRequest c = new TestRequest();
      // e.g. a's connection is closed, its session continues and sends c
      a.onTimeout = () -> timeouts.add(c);
      timeouts.add(a);
      timeouts.add(b);
      long now = System.nanoTime();
      a.timeoutDeadline = now - 1;
      b.timeoutDeadline = now + TimeUnit.SECONDS.toNanos(1);
      assertEquals(1, wakeUps.size());

      timeouts.run();

      assertEquals(1, a.timeouts);
      assertEquals(0, b.timeouts);
      // a single wake-up, at b's deadline rather than at c's
      assertEquals(2, wakeUps.size());
      assertTrue(wakeUps.get(1) <= TimeUnit.SECONDS.toNanos(1), "wake-up in " + wakeUps.get(1) + " ns");
   }

   @Test
   public void testRemovedRequestsDoNotTimeOut() {
      TestRequest[] requests = { new TestRequest(), new TestRequest(), new TestRequest(), new TestRequest() };
      for (TestRequest request : requests) {
         timeouts.add(request);
      }
      // complete out of order (e.g. HTTP/2) and reuse one request (pooled) for a new timeout
      timeouts.remove(requests[2]);
      timeouts.remove(requests[0]);
      timeouts.add(requests[0]);
      timeouts.remove(requests[3]);
      for (TestRequest request : requests) {
         request.timeoutDeadline = System.nanoTime() - 1;
      }

      timeouts.run();

      assertArrayEquals(new int[] { 1, 1, 0, 0 }, new int[] { requests[0].timeouts, requests[1].timeouts,
            requests[2].timeouts, requests[3].timeouts });
      // nothing left to wait for
      assertEquals(1, wakeUps.size());
   }

   @Test
   public void testCompletedAndIdleRequestsDoNotTimeOut() {
      TestRequest completed = new TestRequest();
      TestRequest idle = new TestRequest();
      TestRequest pending = new TestRequest();
      timeouts.add(completed);
      timeouts.add(idle);
      timeouts.add(pending);
      completed.setCompleted();
      idle.setIdle();
      assertNull(((Request) completed).timeouts);
      assertNull(((Request) idle).timeouts);
      for (TestRequest request : new TestRequest[] { completed, idle, pending }) {
         request.timeoutDeadline = System.nanoTime() - 1;
      }

      timeouts.run();

      assertArrayEquals(new int[] { 0, 0, 1 }, new int[] { completed.timeouts, idle.timeouts, pending.timeouts });
   }

   @Test
   public void testCancelOutsideOfEventLoopFails() {
      // the list is not thread-safe: catch any caller that breaks the thread affinity (tests run with -ea)
      TestRequest request = new TestRequest();
      timeouts.add(request);
      inEventLoop = false;
      assertThrows(AssertionError.class, request::setCompleted);
   }

   private static class TestRequest extends Request {
      Runnable onTimeout;
      int timeouts;

      TestRequest() {
         super(null);
      }

      @Override
      public Void call() {
         ++timeouts;
         if (onTimeout != null) {
            onTimeout.run();
         }
         return null;
      }

      @Override
      public void release() {
      }

      @Override
      public long getFiredTimestampMillis(Session session) {
         return 0;
      }

      @Override
      public long getStartTimestampMillis(Session session) {
         return 0;
      }

      @Override
      public long getStartTimestampNanos(Session session) {
         return 0;
      }
   }
}
