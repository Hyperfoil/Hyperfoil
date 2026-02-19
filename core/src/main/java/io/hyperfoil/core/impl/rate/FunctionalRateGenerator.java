package io.hyperfoil.core.impl.rate;

public abstract class FunctionalRateGenerator extends BaseRateGenerator {

   protected abstract long computeFireTimes(long elapsedTimeNs);

   protected abstract double computeFireTimeNs(long targetFireTimes);

   @Override
   public long computeNextFireTime(final long elapsedTimeNs, FireTimeListener listener) {
      if (elapsedTimeNs < fireTimeNs) {
         return (long) Math.ceil(fireTimeNs);
      }
      // How many fire times should have occurred by now (past only, not the next one)
      final long pastFireTimes = computeFireTimes(elapsedTimeNs);
      // +1 is only for scheduling: compute when the NEXT fire time will be
      final long nextFireTimes = pastFireTimes + 1;
      final double nextFireTimeNs = computeFireTimeNs(nextFireTimes);
      fireTimeNs = nextFireTimeNs;
      // Notify listener only about past (actually elapsed) fire times.
      // Using pastFireTimes (not nextFireTimes) keeps this.fireTimes at the correct
      // non-inflated value, so computeFireTimeNs(this.fireTimes + i + 1) produces
      // fire times that match the actual elapsed time — not one interval in the future.
      long missingFireTimes = pastFireTimes - this.fireTimes;
      for (int i = 0; i < missingFireTimes; i++) {
         listener.onFireTime((long) Math.ceil(computeFireTimeNs(this.fireTimes + i + 1)));
      }
      this.fireTimes = pastFireTimes;
      return (long) Math.ceil(nextFireTimeNs);
   }
}
