package io.hyperfoil.core.util;

import java.util.concurrent.atomic.LongAdder;

public class Watermarks {
   protected final LongAdder used = new LongAdder();
   protected volatile int minUsed;
   protected volatile int maxUsed;

   public void incrementUsed() {
      used.increment();
      long currentlyUsed = used.longValue();
      if (currentlyUsed > maxUsed) {
         maxUsed = (int) currentlyUsed;
      }
   }

   public void decrementUsed() {
      decrementUsed(1);
   }

   public void decrementUsed(int num) {
      used.add(-num);
      long currentlyUsed = used.longValue();
      if (currentlyUsed < minUsed) {
         minUsed = (int) currentlyUsed;
      }
   }

   public int minUsed() {
      return minUsed;
   }

   public int maxUsed() {
      return maxUsed;
   }

   public void resetStats() {
      int current = used.intValue();
      minUsed = current;
      maxUsed = current;
   }

   public int current() {
      return used.intValue();
   }
}
