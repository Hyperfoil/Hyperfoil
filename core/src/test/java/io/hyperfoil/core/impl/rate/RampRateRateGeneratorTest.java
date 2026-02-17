package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
      return RateGenerator.rampRate(1, 10, 10_000_000_000L);
   }

   @Test
   public void divisionByZeroTest() {
      final var generator = RateGenerator.rampRate(10, 10, 10_000_000_000L);
      final var missingFireTimeCounter = new FireTimesCounter();
      generator.computeNextFireTime(9_999_000_000L, missingFireTimeCounter);
      assertEquals(100, missingFireTimeCounter.fireTimes);
   }

   @Test
   public void slowStartTest() {
      final var generator = newUserGenerator();
      final var missingFireTimeCounter = new FireTimesCounter();
      generator.computeNextFireTime(9_999_000_000L, missingFireTimeCounter);
      assertEquals(samples(), missingFireTimeCounter.fireTimes);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      // compute inter-arrival times
      final double[] interArrivalTimes = computeInterArrivalTimes(samples);
      // compute fire times on intervals
      final double[] fireTimesOnIntervals = new double[interArrivalTimes.length];
      double elapsedTime = 0;
      for (int i = 0; i < interArrivalTimes.length; i++) {
         final double rpNs = computeRateAtTime(1.0 / 1_000_000_000.0, 10.0 / 1_000_000_000.0, 10_000_000_000L, elapsedTime);
         fireTimesOnIntervals[i] = interArrivalTimes[i] * rpNs;
         elapsedTime += interArrivalTimes[i];
      }
      // we expect each of them to be 1.0
      for (final var fireTime : fireTimesOnIntervals) {
         assertEquals(1.0, fireTime, 0.0);
      }
   }
}
