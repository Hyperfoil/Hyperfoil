package io.hyperfoil.core.impl.rate;

import java.util.Random;

public class PoissonConstantRateGeneratorTest extends RateGeneratorTest {

   private static final int SEED = 0;

   @Override
   int samples() {
      return 1_000;
   }

   @Override
   RateGenerator newUserGenerator() {
      // force the Random::nextDouble to return 0.5
      return RateGenerator.poissonConstantRate(new Random(SEED), 1000);
   }

   @Override
   public void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      // Perform K-S test
      final double[] interArrivalTimes = computeInterArrivalTimes(samples);
      // it is important to use the same SEED here!
      kolmogorovSmirnovTestVsExpDistr(interArrivalTimes, SEED, 1.0);
   }


}
