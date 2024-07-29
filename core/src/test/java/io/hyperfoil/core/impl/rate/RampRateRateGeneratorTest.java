package io.hyperfoil.core.impl.rate;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;

public class RampRateRateGeneratorTest extends RateGeneratorTest {

   private static double computeRateAtTime(final double initialRate, final double targetRate, final long duration,
         final double currentTime) {
      return initialRate + (targetRate - initialRate) * (currentTime / duration);
   }

   @Override
   int samples() {
      // this is using the math series sum formula to calculate the total number of users i.e. sum(1, m) = m * (1 + m) / 2
      // total_users := 10 * (1 + 10) / 2 = 55
      return 55;
   }

   @Override
   RateGenerator newUserGenerator() {
      return RateGenerator.rampRate(1, 10, 10_000);
   }

   @Test
   public void divisionByZeroTest() {
      final var generator = RateGenerator.rampRate(10, 10, 10_000);
      final var missingFireTimeCounter = new FireTimesCounter();
      generator.computeNextFireTime(9999, missingFireTimeCounter);
      Assert.assertEquals(100, missingFireTimeCounter.fireTimes);
   }

   @Test
   public void slowStartTest() {
      final var generator = newUserGenerator();
      final var missingFireTimeCounter = new FireTimesCounter();
      generator.computeNextFireTime(9999, missingFireTimeCounter);
      Assert.assertEquals(samples(), missingFireTimeCounter.fireTimes);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      // compute inter-arrival times
      final double[] interArrivalTimes = computeInterArrivalTimes(samples);
      // compute fire times on intervals
      final double[] fireTimesOnIntervals = new double[interArrivalTimes.length];
      double elapsedTime = 0;
      for (int i = 0; i < interArrivalTimes.length; i++) {
         final double rpMs = computeRateAtTime(1.0 / 1000, 10.0 / 1000, 10_000, elapsedTime);
         fireTimesOnIntervals[i] = interArrivalTimes[i] * rpMs;
         elapsedTime += interArrivalTimes[i];
      }
      // we expect each of them to be 1.0
      for (final var fireTime : fireTimesOnIntervals) {
         assertEquals(1.0, fireTime, 0.0);
      }
   }
}
