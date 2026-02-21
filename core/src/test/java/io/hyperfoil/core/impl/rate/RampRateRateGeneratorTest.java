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
   FireTimeSequence newSequence() {
      return FireTimeSequence.rampRate(1, 10, 10_000_000_000L);
   }

   @Test
   public void divisionByZeroTest() {
      // rampRate with equal initial/target falls back to constantRate
      final var sequence = FireTimeSequence.rampRate(10, 10, 10_000_000_000L);
      // At 10/sec, fire times are at 100ms, 200ms, ... 9900ms = 99 fire times in 9.999s
      long lastFireTime = 0;
      int count = 0;
      while (true) {
         long fireTime = sequence.nextFireTimeNs();
         if (fireTime > 9_999_000_000L) {
            break;
         }
         lastFireTime = fireTime;
         count++;
      }
      assertEquals(99, count);
   }

   @Test
   public void slowStartTest() {
      final var sequence = newSequence();
      // Consume all fire times up to 9.999s
      int count = 0;
      while (true) {
         long fireTime = sequence.nextFireTimeNs();
         if (fireTime > 9_999_000_000L) {
            break;
         }
         count++;
      }
      assertEquals(samples() - 1, count);
   }

   @Override
   void assertSamplesWithoutSkew(final double[] samples) {
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
