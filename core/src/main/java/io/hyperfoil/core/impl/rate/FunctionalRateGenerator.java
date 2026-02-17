package io.hyperfoil.core.impl.rate;

public abstract class FunctionalRateGenerator extends BaseRateGenerator {
   protected abstract long computeFireTimes(long elapsedTimeNs);

   protected abstract double computeFireTimeNs(long targetFireTimes);

   @Override
   public long computeNextFireTime(final long elapsedTimeNs, FireTimeListener listener) {
      if (elapsedTimeNs < fireTimeNs) {
         return (long) Math.ceil(fireTimeNs);
      }
      final long fireTimes = computeFireTimes(elapsedTimeNs) + 1;
      final double nextFireTimeNs = computeFireTimeNs(fireTimes);
      fireTimeNs = nextFireTimeNs;
      long missingFireTimes = fireTimes - this.fireTimes;
      this.fireTimes = fireTimes;
      listener.onFireTimes(missingFireTimes);
      return (long) Math.ceil(nextFireTimeNs);
   }
}
