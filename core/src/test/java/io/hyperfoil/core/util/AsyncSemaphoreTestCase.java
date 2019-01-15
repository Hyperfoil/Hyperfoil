package io.hyperfoil.core.util;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Ignore;
import org.junit.Test;


public class AsyncSemaphoreTestCase {
   public static final int NUM_THREADS = 5;
   public static final int MAX_PERMITS = 3;

   ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
   AtomicInteger executions = new AtomicInteger(10000);
   CountDownLatch latch = new CountDownLatch(NUM_THREADS);

   @Test
   @Ignore
   public void testMultiThreaded() throws Exception {
      AsyncSemaphore asyncSemaphore = new AsyncSemaphore(MAX_PERMITS);
      AtomicInteger counter = new AtomicInteger(0);
      for (int i = 0; i < NUM_THREADS; ++i) {
         executor.submit(() -> runTest(asyncSemaphore, counter));
      }
      latch.await(60, TimeUnit.SECONDS);
   }

   private void runTest(AsyncSemaphore sem, AtomicInteger counter) {
      sem.acquire(() -> {
         try {
            int value = counter.incrementAndGet();
            assertTrue("Value: " + value, value <= MAX_PERMITS);
         } finally {
            sem.release();
         }
         if (executions.decrementAndGet() > 0) {
            executor.submit(() -> runTest(sem, counter));
         } else {
            latch.countDown();
         }
      });
   }
}
