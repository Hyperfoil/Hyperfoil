package io.hyperfoil.core.impl.rate;

@FunctionalInterface
public interface FireTimeListener {

   void onFireTime();

   default void onFireTimes(long count) {
      for (long i = 0; i < count; i++) {
         onFireTime();
      }
   }

}
