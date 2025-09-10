package io.hyperfoil.core.impl.rate;

public abstract class FunctionalRateGenerator extends BaseRateGenerator {

   private long[] missingFireTimeMs = new long[1];

   private void ensureCapacity(long expectedCapacity) {
      if (expectedCapacity >= this.missingFireTimeMs.length) {
         long[] newFireTimes = new long[(int) (expectedCapacity * 1.5)];
         System.arraycopy(this.missingFireTimeMs, 0, newFireTimes, 0, this.missingFireTimeMs.length);
         this.missingFireTimeMs = newFireTimes;
      }
   }

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
      ensureCapacity(missingFireTimes);
      // compute the missing fire times (which includes the current one)
      for (int i = 0; i < missingFireTimes; i++) {
         missingFireTimeMs[i] = (long) Math.ceil(computeFireTimeMs(this.fireTimes + i));
      }
      this.fireTimes = fireTimes;
      listener.onFireTimes(missingFireTimeMs, missingFireTimes);
      return (long) Math.ceil(nextFireTimeMs);
   }
}
