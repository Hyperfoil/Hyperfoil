package io.hyperfoil.core.impl.rate;

abstract class SequentialRateGenerator extends BaseRateGenerator {

   protected abstract double nextFireTimeMs(double elapsedTimeMs);

   @Override
   public final long computeNextFireTime(final long elapsedTimeMs, FireTimeListener listener) {
      long fireTimesToMillis = 0;
      double nextFireTimeMs = fireTimeMs;
      while (elapsedTimeMs >= nextFireTimeMs) {
         listener.onFireTime();
         fireTimesToMillis++;
         nextFireTimeMs = nextFireTimeMs(nextFireTimeMs);
      }
      fireTimeMs = nextFireTimeMs;
      this.fireTimes += fireTimesToMillis;
      return (long) Math.ceil(nextFireTimeMs);
   }
}
