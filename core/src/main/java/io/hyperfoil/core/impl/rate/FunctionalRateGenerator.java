package io.hyperfoil.core.impl.rate;

public abstract class FunctionalRateGenerator extends BaseRateGenerator {

   private long[] missingFireTimeNs = new long[1];

   private void ensureCapacity(long expectedCapacity) {
      if (expectedCapacity >= this.missingFireTimeNs.length) {
         long[] newFireTimes = new long[(int) (expectedCapacity * 1.5)];
         System.arraycopy(this.missingFireTimeNs, 0, newFireTimes, 0, this.missingFireTimeNs.length);
         this.missingFireTimeNs = newFireTimes;
      }
   }

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
      ensureCapacity(missingFireTimes);
      // compute the missing fire times (which includes the current one)
      for (int i = 0; i < missingFireTimes; i++) {
         missingFireTimeNs[i] = (long) Math.ceil(computeFireTimeNs(this.fireTimes + i + 1));
      }
      this.fireTimes = fireTimes;
      listener.onFireTimes(missingFireTimeNs, missingFireTimes);
      return (long) Math.ceil(nextFireTimeNs);
   }
}
