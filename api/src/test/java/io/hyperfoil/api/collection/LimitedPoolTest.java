package io.hyperfoil.api.collection;

import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;

public class LimitedPoolTest {
   @Test
   public void test() {
      final int size = 16;
      LimitedPool<Integer> pool = new LimitedPool<>(size, () -> 0);
      ArrayDeque<Integer> queue = new ArrayDeque<>(size);
      for (int i = 0; i < 10000; ++i) {
         if (queue.isEmpty()) {
            queue.push(pool.acquire());
         } else if (queue.size() == size || ThreadLocalRandom.current().nextBoolean()) {
            pool.release(queue.poll());
         } else {
            queue.push(pool.acquire());
         }
      }
   }
}
