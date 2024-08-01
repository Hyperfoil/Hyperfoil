package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecreasingRumpRateGeneratorTest extends RateGeneratorTest {

   @Override
   int samples() {
      // this is using the math series sum formula to calculate the total number of users i.e. sum(1, m) = m * (1 + m) / 2
      // total_users := 10 * (1 + 10) / 2 = 55
      return 55;
   }

   @Override
   RateGenerator newUserGenerator() {
      return RateGenerator.rampRate(10, 1, 10_000);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      assertEquals(10_000, samples[samples.length - 1], 0.0);
   }
}
