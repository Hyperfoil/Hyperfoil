package io.hyperfoil.core.impl.rate;

@FunctionalInterface
public interface FireTimeListener {

   void onFireTime(long fireTimeNs);

   default void onFireTimes(long[] fireTimesNs, long count) {
      for (int i = 0; i < count; i++) {
         onFireTime(fireTimesNs[i]);
      }
   }

}
