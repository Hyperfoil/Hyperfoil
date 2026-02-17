package io.hyperfoil.core.impl.rate;

import java.util.Random;

public interface RateGenerator {

   long computeNextFireTime(long elapsedNanos, FireTimeListener listener);

   long lastComputedFireTimeNs();

   long fireTimes();

   static RateGenerator constantRate(double fireTimesPerSec) {
      return new ConstantRateGenerator(fireTimesPerSec);
   }

   static RateGenerator rampRate(double initialFireTimesPerSec, double targetFireTimesPerSec, long durationNs) {
      if (Math.abs(targetFireTimesPerSec - initialFireTimesPerSec) < 0.000001) {
         return constantRate(initialFireTimesPerSec);
      }
      return new RampRateGenerator(initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }

   static RateGenerator poissonConstantRate(Random random, double usersPerSec) {
      return new PoissonConstantRateGenerator(random, usersPerSec);
   }

   static RateGenerator poissonConstantRate(double usersPerSec) {
      return poissonConstantRate(new Random(), usersPerSec);
   }

   static RateGenerator poissonRampRate(Random random, double initialFireTimesPerSec, double targetFireTimesPerSec,
         long durationNs) {
      if (Math.abs(targetFireTimesPerSec - initialFireTimesPerSec) < 0.000001) {
         return poissonConstantRate(random, initialFireTimesPerSec);
      }
      return new PoissonRampRateGenerator(random, initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }

   static RateGenerator poissonRampRate(double initialFireTimesPerSec, double targetFireTimesPerSec, long durationNs) {
      return poissonRampRate(new Random(), initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }
}
