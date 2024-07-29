package io.hyperfoil.core.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Watermarks {
   private static final Logger log = LogManager.getLogger(Watermarks.class);
   // The fields are volatile as these can be read by any thread,
   // but written only by the event-loop executor thread.
   protected volatile int used = 0;
   protected volatile int minUsed;
   protected volatile int maxUsed;

   public void incrementUsed() {
      assert used >= 0;
      //noinspection NonAtomicOperationOnVolatileField
      used++;
      if (used > maxUsed) {
         maxUsed = (int) (long) used;
      }
   }

   public void decrementUsed() {
      decrementUsed(1);
   }

   public void decrementUsed(int num) {
      //noinspection NonAtomicOperationOnVolatileField
      used -= num;
      if (used < minUsed) {
         minUsed = (int) (long) used;
      }
      assert used >= 0;
   }

   public int minUsed() {
      return minUsed;
   }

   public int maxUsed() {
      return maxUsed;
   }

   public void resetStats() {
      minUsed = used;
      maxUsed = used;
   }

   public int current() {
      return used;
   }
}
