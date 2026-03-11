package io.hyperfoil.core.impl.rate;

import java.util.Random;

public class PoissonConstantRateGeneratorTest extends RateGeneratorTest {

   private static final int SEED = 0;

   @Override
   int samples() {
      return 1_000;
   }

   @Override
   FireTimeSequence newSequence() {
      return FireTimeSequence.poissonConstantRate(new Random(SEED), 1000);
   }

   @Override
   public void assertSamplesWithoutSkew(final double[] samples) {
      // Perform K-S test
      final double[] interArrivalTimes = computeInterArrivalTimes(samples);
      // it is important to use the same SEED here!
      kolmogorovSmirnovTestVsExpDistr(interArrivalTimes, SEED, 1_000_000.0);
   }

}
