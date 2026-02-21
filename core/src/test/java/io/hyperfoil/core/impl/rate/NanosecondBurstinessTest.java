package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests that verify fire time sequences produce properly sub-millisecond-spaced values
 * at high rates, rather than bunching at millisecond boundaries.
 */
public class NanosecondBurstinessTest {

   @Test
   public void constantRateHighRateNoMillisecondBursts() {
      final double rate = 10_000; // 10,000 users/sec
      final long expectedInterArrivalNs = 100_000L; // 0.1 ms
      final FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      final int samples = 100;
      final long[] fireTimes = new long[samples];
      for (int i = 0; i < samples; i++) {
         fireTimes[i] = sequence.nextFireTimeNs();
      }

      // Verify proper sub-millisecond spacing
      for (int i = 1; i < samples; i++) {
         long interArrival = fireTimes[i] - fireTimes[i - 1];
         assertEquals(expectedInterArrivalNs, interArrival,
               "Inter-arrival time at index " + i + " should be " + expectedInterArrivalNs + " ns but was " + interArrival);
      }

      // Verify that fire times span multiple sub-millisecond values
      int distinctMillisValues = 0;
      long prevMs = fireTimes[0] / 1_000_000;
      for (int i = 1; i < samples; i++) {
         long currentMs = fireTimes[i] / 1_000_000;
         if (currentMs != prevMs) {
            distinctMillisValues++;
            prevMs = currentMs;
         }
      }
      // 100 samples at 100,000 ns apart = 10,000,000 ns = 10 ms total span
      assertTrue(distinctMillisValues >= 9,
            "Fire times should span multiple millisecond values, but only had " + distinctMillisValues + " transitions");
   }

   @Test
   public void constantRateVeryHighRateSubMillisecondSpacing() {
      final double rate = 100_000; // 100,000 users/sec
      final long expectedInterArrivalNs = 10_000L; // 10 us
      final FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      final int samples = 1000;
      final long[] fireTimes = new long[samples];
      for (int i = 0; i < samples; i++) {
         fireTimes[i] = sequence.nextFireTimeNs();
      }

      // All fire times should be distinct and properly spaced
      for (int i = 1; i < samples; i++) {
         assertTrue(fireTimes[i] > fireTimes[i - 1],
               "Fire times must be strictly increasing at index " + i);
         assertEquals(expectedInterArrivalNs, fireTimes[i] - fireTimes[i - 1],
               "Inter-arrival at index " + i + " should be " + expectedInterArrivalNs + " ns");
      }
   }

   @Test
   public void rampRateHighRatePortionHasSubMillisecondSpacing() {
      final long durationNs = 10_000_000_000L; // 10 seconds
      final FireTimeSequence sequence = FireTimeSequence.rampRate(1_000, 100_000, durationNs);

      // Skip to 9 seconds (near the end where rate ~ 91,000 users/sec)
      long fireTime;
      do {
         fireTime = sequence.nextFireTimeNs();
      } while (fireTime < 9_000_000_000L);

      // Now collect fire times near the high-rate end
      long[] highRateFireTimes = new long[50];
      highRateFireTimes[0] = fireTime;
      for (int i = 1; i < highRateFireTimes.length; i++) {
         highRateFireTimes[i] = sequence.nextFireTimeNs();
      }

      // Verify all inter-arrival times are sub-millisecond (< 1,000,000 ns)
      for (int i = 1; i < highRateFireTimes.length; i++) {
         long interArrival = highRateFireTimes[i] - highRateFireTimes[i - 1];
         assertTrue(interArrival > 0, "Fire times must be strictly increasing at index " + i);
         assertTrue(interArrival < 1_000_000,
               "Inter-arrival at high rate should be sub-millisecond, was " + interArrival + " ns at index " + i);
      }
   }

   @Test
   public void poissonConstantRateHighRateSubMillisecondSpacing() {
      final double rate = 10_000; // 10,000 users/sec -> mean inter-arrival = 100 us
      final FireTimeSequence sequence = FireTimeSequence.poissonConstantRate(new Random(42), rate);

      final int samples = 1000;
      final long[] fireTimes = new long[samples];
      for (int i = 0; i < samples; i++) {
         fireTimes[i] = sequence.nextFireTimeNs();
      }

      // Compute mean inter-arrival time
      double sumInterArrival = 0;
      int subMillisecondCount = 0;
      for (int i = 1; i < samples; i++) {
         long interArrival = fireTimes[i] - fireTimes[i - 1];
         assertTrue(interArrival > 0, "Fire times must be strictly increasing");
         sumInterArrival += interArrival;
         if (interArrival < 1_000_000) {
            subMillisecondCount++;
         }
      }
      double meanInterArrival = sumInterArrival / (samples - 1);

      // Mean should be close to 100,000 ns (100 us) -- allow 30% tolerance for randomness
      assertTrue(meanInterArrival > 70_000 && meanInterArrival < 130_000,
            "Mean inter-arrival should be ~100,000 ns, was " + meanInterArrival);

      // Most inter-arrivals should be sub-millisecond
      assertTrue(subMillisecondCount > samples * 0.99,
            "At least 99% of inter-arrivals should be sub-millisecond, was " + subMillisecondCount + "/" + (samples - 1));
   }

   @Test
   public void poissonRampRateHighRateNoMillisecondBursts() {
      final long durationNs = 10_000_000_000L; // 10 seconds
      final FireTimeSequence sequence = FireTimeSequence.poissonRampRate(
            new Random(42), 1_000, 50_000, durationNs);

      // Skip to 9 seconds to get near the high rate (~45,100 users/sec)
      long fireTime;
      do {
         fireTime = sequence.nextFireTimeNs();
      } while (fireTime < 9_000_000_000L);

      // Collect fire times near the high-rate end
      long[] highRateFireTimes = new long[100];
      highRateFireTimes[0] = fireTime;
      for (int i = 1; i < highRateFireTimes.length; i++) {
         highRateFireTimes[i] = sequence.nextFireTimeNs();
      }

      // At ~45,000 users/sec, mean inter-arrival ~ 22 us
      int subMillisecondCount = 0;
      for (int i = 1; i < highRateFireTimes.length; i++) {
         long interArrival = highRateFireTimes[i] - highRateFireTimes[i - 1];
         assertTrue(interArrival > 0, "Fire times must be strictly increasing at index " + i);
         if (interArrival < 1_000_000) {
            subMillisecondCount++;
         }
      }

      assertTrue(subMillisecondCount > 90,
            "At ~45,000 users/sec most inter-arrivals should be sub-ms, was " + subMillisecondCount + "/99");
   }
}
