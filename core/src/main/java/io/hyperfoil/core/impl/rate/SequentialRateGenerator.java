package io.hyperfoil.core.impl.rate;

abstract class SequentialRateGenerator extends BaseRateGenerator {

   protected abstract double nextFireTimeNs(double elapsedTimeNs);

   @Override
   public final long computeNextFireTime(final long elapsedTimeNs, FireTimeListener listener) {
      long fireTimesCount = 0;
      double nextFireTimeNs = fireTimeNs;
      while (elapsedTimeNs >= nextFireTimeNs) {
         listener.onFireTime((long) Math.ceil(nextFireTimeNs));
         fireTimesCount++;
         nextFireTimeNs = nextFireTimeNs(nextFireTimeNs);
      }
      fireTimeNs = nextFireTimeNs;
      this.fireTimes += fireTimesCount;
      return (long) Math.ceil(nextFireTimeNs);
   }
}
