package io.hyperfoil.core.impl.rate;

import java.util.Random;

public class PoissonRampRateGeneratorTest extends RateGeneratorTest {

   private static final int SEED = 0;

   private static double computeRateAtTime(final double initialRate, final double targetRate, final long duration,
         final double currentTime) {
      return initialRate + (targetRate - initialRate) * (currentTime / duration);
   }

   @Override
   int samples() {
      return 1000;
   }

   @Override
   FireTimeSequence newSequence() {
      return FireTimeSequence.poissonRampRate(new Random(SEED), 1, 10, 10_000_000_000L);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples) {
      // For a linearly changing rate, the number of events in [t1, t2] is the integral of rate(t) over that interval.
      // For a linear function, ∫[t1,t2] rate(t) dt = rate_at_midpoint * (t2 - t1).
      final double[] fireTimesOnIntervals = new double[samples.length - 1];
      for (int i = 0; i < samples.length - 1; i++) {
         final double t1 = samples[i];
         final double t2 = samples[i + 1];
         final double midpoint = (t1 + t2) / 2;
         final double rateAtMidpoint = computeRateAtTime(1.0 / 1_000_000_000.0, 10.0 / 1_000_000_000.0, 10_000_000_000L,
               midpoint);
         fireTimesOnIntervals[i] = (t2 - t1) * rateAtMidpoint;
      }
      // fireTimesOnIntervals should follow an exponential distribution with lambda = 1
      kolmogorovSmirnovTestVsExpDistr(fireTimesOnIntervals, SEED, 1.0);
   }
}
