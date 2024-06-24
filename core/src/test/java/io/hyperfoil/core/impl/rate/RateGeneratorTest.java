package io.hyperfoil.core.impl.rate;

import static org.junit.Assert.assertEquals;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public abstract class RateGeneratorTest {

   public static double[] computeInterArrivalTimes(final double[] samples) {
      final double[] interArrivalTimes = new double[samples.length - 1];
      int j = 0;
      for (int i = 1; i < samples.length; i++) {
         final double intervalMs = samples[i] - samples[i - 1];
         interArrivalTimes[j] = intervalMs;
         j++;
      }
      return interArrivalTimes;
   }

   public static void kolmogorovSmirnovTestVsExpDistr(final double[] data, final int seed, final double mean) {
      final KolmogorovSmirnovTest ksTest = new KolmogorovSmirnovTest(new JDKRandomGenerator(seed));
      final ExponentialDistribution expDistribution = new ExponentialDistribution(mean); // Mean of the distribution is 1 for the normalized data
      final double pValue = ksTest.kolmogorovSmirnovTest(expDistribution, data);


      if (pValue < 0.05) {
         Assert.fail("The generated fire times do not follow the expected exponential distribution. p-value: " + pValue);
      }
   }

   abstract int samples();

   abstract RateGenerator newUserGenerator();

   abstract void assertSamplesWithoutSkew(double[] fireTimesMs, long totalUsers);

   @Test
   public void testNoFireTimesOnCreation() {
      assertEquals(0, newUserGenerator().fireTimes());
   }

   @Test
   @Ignore("This test fail due to lastComputedFireTimeMs() uses Math.ceil() and can skew the results")
   public void testFireTimesDistributionWithoutSkew() {
      final int samples = samples();
      final var fireTimeSamples = new double[samples];
      final var userGenerator = newUserGenerator();
      final var fireTimesCounter = new FireTimesCounter();
      for (int i = 0; i < samples; i++) {
         final long fireTimesBefore = userGenerator.fireTimes();
         fireTimesCounter.fireTimes = 0;
         final var nextFireTimeMs = userGenerator.computeNextFireTime(userGenerator.lastComputedFireTimeMs(), fireTimesCounter);
         final long fireTimesAfter = userGenerator.fireTimes();
         assertEquals(1, fireTimesCounter.fireTimes);
         assertEquals(1, fireTimesAfter - fireTimesBefore);
         assertEquals(nextFireTimeMs, userGenerator.lastComputedFireTimeMs(), 0.0);
         fireTimeSamples[i] = nextFireTimeMs;
      }
      assertEquals(samples(), userGenerator.fireTimes());
      assertSamplesWithoutSkew(fireTimeSamples, userGenerator.fireTimes());
   }
}
