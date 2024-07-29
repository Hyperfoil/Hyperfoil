package io.hyperfoil.core.impl.rate;

public abstract class FunctionalRateGenerator extends BaseRateGenerator {
   protected abstract long computeFireTimes(long elapsedTimeMs);

   protected abstract double computeFireTimeMs(long targetFireTimes);

   @Override
   public long computeNextFireTime(final long elapsedTimeMs, FireTimeListener listener) {
      if (elapsedTimeMs < fireTimeMs) {
         return (long) Math.ceil(fireTimeMs);
      }
      final long fireTimes = computeFireTimes(elapsedTimeMs) + 1;
      final double nextFireTimeMs = computeFireTimeMs(fireTimes);
      fireTimeMs = nextFireTimeMs;
      long missingFireTimes = fireTimes - this.fireTimes;
      this.fireTimes = fireTimes;
      listener.onFireTimes(missingFireTimes);
      return (long) Math.ceil(nextFireTimeMs);
   }
}
