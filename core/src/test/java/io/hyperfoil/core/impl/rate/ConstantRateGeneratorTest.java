package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConstantRateGeneratorTest extends RateGeneratorTest {
   @Override
   int samples() {
      return 1000;
   }

   @Override
   FireTimeSequence newSequence() {
      return FireTimeSequence.constantRate(1000);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples) {
      for (int i = 1; i < samples.length; ++i) {
         assertEquals(samples[i - 1] + 1_000_000.0, samples[i], 0.0);
      }
      assertEquals(999_000_000.0, samples[samples.length - 1] - samples[0], 0.0);
   }
}
