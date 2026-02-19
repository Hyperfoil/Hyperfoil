package io.hyperfoil.core.impl.rate;

final class FireTimesCounter implements FireTimeListener {

   public long fireTimes;

   FireTimesCounter() {
      fireTimes = 0;
   }

   @Override
   public void onFireTime(long fireTimeNs) {
      fireTimes++;
   }
}
