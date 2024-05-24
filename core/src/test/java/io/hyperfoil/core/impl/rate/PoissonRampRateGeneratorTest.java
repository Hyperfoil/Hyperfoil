package io.hyperfoil.core.impl.rate;

import java.util.Random;

public class PoissonRampRateGeneratorTest extends RateGeneratorTest {

   private static final int SEED = 0;

   private static double computeRateAtTime(final double initialRate, final double targetRate, final long duration, final double currentTime) {
      return initialRate + (targetRate - initialRate) * (currentTime / duration);
   }

   @Override
   int samples() {
      return 1000;
   }

   @Override
   RateGenerator newUserGenerator() {
      return RateGenerator.poissonRampRate(new Random(SEED), 1, 10, 10000);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      final double[] interArrivalTimes = computeInterArrivalTimes(samples);
      final double[] fireTimesOnIntervals = new double[interArrivalTimes.length];
      double elapsedTime = 0;
      for (int i = 0; i < interArrivalTimes.length; i++) {
         final double rpMs = computeRateAtTime(0.001, 0.01, 10000, elapsedTime);
         fireTimesOnIntervals[i] = interArrivalTimes[i] * rpMs;
         elapsedTime += interArrivalTimes[i];
      }
      // fireTimesOnIntervals should follow an exponential distribution with lambda = 1
      kolmogorovSmirnovTestVsExpDistr(fireTimesOnIntervals, SEED, 1.0);
   }
}
