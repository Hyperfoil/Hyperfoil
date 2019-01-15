package io.hyperfoil.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class RandomConcurrentSetTest {
   public static final int ALLOCATING_THREADS = 3;
   public static final int REUSING_THREADS = 3;
   public static final int MAX = 100000;

   ExecutorService executor = Executors.newFixedThreadPool(ALLOCATING_THREADS + REUSING_THREADS);
   AtomicInteger counter = new AtomicInteger();
   CountDownLatch latch = new CountDownLatch(ALLOCATING_THREADS + REUSING_THREADS);
   AtomicReference<Throwable> error = new AtomicReference<>();

   @Test
   public void testMultiThreaded() throws Exception {
      RandomConcurrentSet<Integer> set = new RandomConcurrentSet<>(16, 16, 16);
      for (int i = 0; i < ALLOCATING_THREADS; ++i) {
         executor.submit(() -> runAllocator(set));
      }
      for (int i = 0; i < REUSING_THREADS; ++i) {
         executor.submit(() -> runReusing(set));
      }
      latch.await(60, TimeUnit.SECONDS);
      if (error.get() != null) {
         throw new AssertionError(error.get());
      }
      BitSet bitSet = new BitSet(MAX);
      AtomicInteger values = new AtomicInteger();
      set.readAll(value -> {
         assertThat(value).isLessThan(MAX);
         assertThat(bitSet.get(value)).as("duplicit value %d", value).isFalse();
         bitSet.set(value);
         values.incrementAndGet();
      });
      for (int i = 0; i < MAX; ++i) {
         assertThat(bitSet.get(i)).as("missing value %d", i).isTrue();
      }
      assertThat(values.get()).isEqualTo(MAX);
   }

   private void runReusing(RandomConcurrentSet<Integer> set) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      try {
         for (; ; ) {
            if (counter.get() >= MAX) {
               return;
            }
            Integer value = set.fetch();
            if (value == null) {
               Thread.yield();
            } else {
               if (random.nextBoolean()) {
                  Thread.yield();
               }
               set.put(value);
            }
         }
      } catch (Throwable t) {
         error.set(t);
      } finally {
         latch.countDown();
      }
   }

   private void runAllocator(RandomConcurrentSet<Integer> set) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      try {
         for (;;) {
            int value = counter.getAndIncrement();
            if (value >= MAX) {
               return;
            }
            set.put(value);
            if (random.nextBoolean()) {
               Thread.yield();
            }
         }
      } catch (Throwable t) {
         error.set(t);
      } finally {
         latch.countDown();
      }
   }

}
