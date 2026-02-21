package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.junit.jupiter.api.Test;

public abstract class RateGeneratorTest {

   public static double[] computeInterArrivalTimes(final double[] samples) {
      final double[] interArrivalTimes = new double[samples.length - 1];
      for (int i = 1; i < samples.length; i++) {
         interArrivalTimes[i - 1] = samples[i] - samples[i - 1];
      }
      return interArrivalTimes;
   }

   public static void kolmogorovSmirnovTestVsExpDistr(final double[] data, final int seed, final double mean) {
      final KolmogorovSmirnovTest ksTest = new KolmogorovSmirnovTest(new JDKRandomGenerator(seed));
      final ExponentialDistribution expDistribution = new ExponentialDistribution(mean);
      final double pValue = ksTest.kolmogorovSmirnovTest(expDistribution, data);

      if (pValue < 0.05) {
         fail("The generated fire times do not follow the expected exponential distribution. p-value: " + pValue);
      }
   }

   abstract int samples();

   abstract FireTimeSequence newSequence();

   abstract void assertSamplesWithoutSkew(double[] fireTimesNs);

   @Test
   public void testFireTimesAreStrictlyIncreasing() {
      final var sequence = newSequence();
      long prev = -1;
      for (int i = 0; i < samples(); i++) {
         long fireTime = sequence.nextFireTimeNs();
         assertTrue(fireTime > prev, "Fire times must be strictly increasing at index " + i
               + ": prev=" + prev + " current=" + fireTime);
         prev = fireTime;
      }
   }

   @Test
   public void testFireTimesDistributionWithoutSkew() {
      final int samples = samples();
      final var fireTimeSamples = new double[samples];
      final var sequence = newSequence();
      for (int i = 0; i < samples; i++) {
         fireTimeSamples[i] = sequence.nextFireTimeNs();
      }
      assertSamplesWithoutSkew(fireTimeSamples);
   }
}
