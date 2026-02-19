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
      assertEquals(99, missingFireTimeCounter.fireTimes);
   }

   @Test
   public void slowStartTest() {
      final var generator = newUserGenerator();
      final var missingFireTimeCounter = new FireTimesCounter();
      generator.computeNextFireTime(9_999_000_000L, missingFireTimeCounter);
      assertEquals(samples() - 1, missingFireTimeCounter.fireTimes);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples, final long totalUsers) {
      // For a linearly changing rate, the number of events in [t1, t2] is the integral of rate(t) over that interval.
      // For a linear function, ∫[t1,t2] rate(t) dt = rate_at_midpoint * (t2 - t1).
      final double initialRatePerNs = 1.0 / 1_000_000_000.0;
      final double targetRatePerNs = 10.0 / 1_000_000_000.0;
      final long durationNs = 10_000_000_000L;
      for (int i = 0; i < samples.length - 1; i++) {
         final double t1 = samples[i];
         final double t2 = samples[i + 1];
         final double midpoint = (t1 + t2) / 2;
         final double rateAtMidpoint = computeRateAtTime(initialRatePerNs, targetRatePerNs, durationNs, midpoint);
         final double integral = (t2 - t1) * rateAtMidpoint;
         // each interval should contain exactly one fire event (integral of rate over interval = 1)
         assertEquals(1.0, integral, 1e-6, "interval " + i);
      }
   }
}
