package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
         fail("The generated fire times do not follow the expected exponential distribution. p-value: " + pValue);
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
   public void testLostFireTimesWithoutDelays() {
      final int samples = samples();
      final var userGenerator = newUserGenerator();
      final var fireTimesCollector = new FireTimesCollector(samples * 2);
      final long[] fireTimes = new long[samples];
      fireTimes[0] = userGenerator.lastComputedFireTimeMs();
      for (int i = 1; i < samples; i++) {
         int samplesBefore = fireTimesCollector.size();
         fireTimes[i] = userGenerator.computeNextFireTime(userGenerator.lastComputedFireTimeMs(), fireTimesCollector);
         assertEquals(samplesBefore + 1, fireTimesCollector.size(), "Sample " + i);
      }
      userGenerator.computeNextFireTime(userGenerator.lastComputedFireTimeMs(), fireTimesCollector);
      assertArrayEquals(fireTimes, Arrays.copyOf(fireTimesCollector.fireTimes(), fireTimes.length));
      var orderedFireTimes = Arrays.copyOf(fireTimesCollector.fireTimes(), fireTimesCollector.size());
      var unorderedFireTimes = orderedFireTimes.clone();
      Arrays.sort(orderedFireTimes);
      assertArrayEquals(orderedFireTimes, unorderedFireTimes);
   }

   private static final class FireTimesCollector implements FireTimeListener {

      private final long[] fireTimes;
      private int index = 0;

      FireTimesCollector(int samples) {
         fireTimes = new long[samples];
      }

      @Override
      public void onFireTime(long fireTimeMs) {
         fireTimes[index++] = fireTimeMs;
      }

      public long[] fireTimes() {
         return fireTimes;
      }

      public int size() {
         return index;
      }
   }

   @Test
   @Disabled("This test fail due to lastComputedFireTimeMs() uses Math.ceil() and can skew the results")
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
