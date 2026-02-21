package io.hyperfoil.core.impl.rate;

import java.util.Random;

/**
 * A monotonic sequence of fire times in nanoseconds (relative to phase start).
 * <p>
 * Each call to {@link #nextFireTimeNs()} advances the sequence and returns
 * the next fire time. The caller is responsible for deciding when to consume
 * the next value (i.e., comparing against elapsed time).
 */
public interface FireTimeSequence {

   /**
    * Produce the next fire time in the sequence, advancing internal state.
    *
    * @return the next fire time in nanoseconds
    */
   long nextFireTimeNs();

   static FireTimeSequence constantRate(double fireTimesPerSec) {
      return new ConstantRateGenerator(fireTimesPerSec);
   }

   static FireTimeSequence rampRate(double initialFireTimesPerSec, double targetFireTimesPerSec, long durationNs) {
      if (Math.abs(targetFireTimesPerSec - initialFireTimesPerSec) < 0.000001) {
         return constantRate(initialFireTimesPerSec);
      }
      return new RampRateGenerator(initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }

   static FireTimeSequence poissonConstantRate(Random random, double usersPerSec) {
      return new PoissonConstantRateGenerator(random, usersPerSec);
   }

   static FireTimeSequence poissonConstantRate(double usersPerSec) {
      return poissonConstantRate(new Random(), usersPerSec);
   }

   static FireTimeSequence poissonRampRate(Random random, double initialFireTimesPerSec, double targetFireTimesPerSec,
         long durationNs) {
      if (Math.abs(targetFireTimesPerSec - initialFireTimesPerSec) < 0.000001) {
         return poissonConstantRate(random, initialFireTimesPerSec);
      }
      return new PoissonRampRateGenerator(random, initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }

   static FireTimeSequence poissonRampRate(double initialFireTimesPerSec, double targetFireTimesPerSec, long durationNs) {
      return poissonRampRate(new Random(), initialFireTimesPerSec, targetFireTimesPerSec, durationNs);
   }
}
