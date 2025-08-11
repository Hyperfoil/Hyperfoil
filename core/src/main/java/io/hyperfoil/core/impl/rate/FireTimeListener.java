package io.hyperfoil.core.impl.rate;

@FunctionalInterface
public interface FireTimeListener {

   void onFireTime(long fireTimeMs);

   default void onFireTimes(long[] fireTimeMs, long count) {
      for (int i = 0; i < count; i++) {
         onFireTime(fireTimeMs[i]);
      }
   }

}
