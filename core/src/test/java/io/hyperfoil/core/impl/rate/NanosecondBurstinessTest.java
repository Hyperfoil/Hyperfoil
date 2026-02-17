package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Tests that verify the nanosecond-based rate generators do not exhibit "bursty" behavior.
 * <p>
 * With the old millisecond-based scheduling, rates above ~1000 users/sec would produce
 * inter-arrival times below 1ms. Since the scheduler used {@code TimeUnit.MILLISECONDS},
 * these sub-ms delays truncated to 0, causing bursts of events at millisecond boundaries.
 * <p>
 * For example, at 10,000 users/sec the expected inter-arrival time is 100,000 ns (0.1 ms).
 * With ms-based scheduling, 10 events would fire at the same millisecond tick.
 * With ns-based scheduling, each event gets its own distinct fire time spaced 100µs apart.
 */
public class NanosecondBurstinessTest {

   /**
    * At 10,000 users/sec the inter-arrival time is exactly 100,000 ns.
    * Verify that each consecutive fire time is spaced exactly 100,000 ns apart
    * and that no two fire times share the same millisecond-truncated value
    * (which would indicate bursting).
    */
   @Test
   public void constantRateHighRateNoMillisecondBursts() {
      final double rate = 10_000; // 10,000 users/sec
      final long expectedInterArrivalNs = 100_000L; // 0.1 ms
      final RateGenerator generator = RateGenerator.constantRate(rate);
      final FireTimesCounter counter = new FireTimesCounter();

      final int samples = 100;
      final long[] fireTimes = new long[samples];

      for (int i = 0; i < samples; i++) {
         counter.fireTimes = 0;
         long elapsed = generator.lastComputedFireTimeNs();
         fireTimes[i] = generator.computeNextFireTime(elapsed, counter);
         assertEquals(1, counter.fireTimes, "Each call should produce exactly one fire time");
      }

      // Verify proper sub-millisecond spacing
      for (int i = 1; i < samples; i++) {
         long interArrival = fireTimes[i] - fireTimes[i - 1];
         assertEquals(expectedInterArrivalNs, interArrival,
               "Inter-arrival time at index " + i + " should be " + expectedInterArrivalNs + " ns but was " + interArrival);
      }

      // Verify that fire times span multiple sub-millisecond values
      // (i.e., they do NOT all collapse to the same ms boundary)
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
      // So we should see roughly 10 distinct ms values
      assertTrue(distinctMillisValues >= 9,
            "Fire times should span multiple millisecond values, but only had " + distinctMillisValues + " transitions");
   }

   /**
    * At 100,000 users/sec the inter-arrival time is 10,000 ns (10 µs).
    * In the old ms-based system, 100 events per ms would fire at once.
    * Verify that each event has a distinct, properly-spaced fire time.
    */
   @Test
   public void constantRateVeryHighRateSubMillisecondSpacing() {
      final double rate = 100_000; // 100,000 users/sec
      final long expectedInterArrivalNs = 10_000L; // 10 µs
      final RateGenerator generator = RateGenerator.constantRate(rate);
      final FireTimesCounter counter = new FireTimesCounter();

      final int samples = 1000;
      final long[] fireTimes = new long[samples];

      for (int i = 0; i < samples; i++) {
         counter.fireTimes = 0;
         long elapsed = generator.lastComputedFireTimeNs();
         fireTimes[i] = generator.computeNextFireTime(elapsed, counter);
         assertEquals(1, counter.fireTimes);
      }

      // All fire times should be distinct
      for (int i = 1; i < samples; i++) {
         assertTrue(fireTimes[i] > fireTimes[i - 1],
               "Fire times must be strictly increasing at index " + i);
         assertEquals(expectedInterArrivalNs, fireTimes[i] - fireTimes[i - 1],
               "Inter-arrival at index " + i + " should be " + expectedInterArrivalNs + " ns");
      }

      // Verify total count is correct
      assertEquals(samples, generator.fireTimes());
   }

   /**
    * Verify that when many events are requested at once (simulating a large elapsed time jump),
    * the computed fire times are correctly spaced, not bunched at the start.
    */
   @Test
   public void constantRateCatchUpDoesNotBurst() {
      final double rate = 10_000; // 10,000 users/sec
      final RateGenerator generator = RateGenerator.constantRate(rate);
      final FireTimesCounter counter = new FireTimesCounter();

      // Simulate 10ms elapsed at once
      // computeFireTimes(10_000_000) = (long)(10_000_000 * 10_000 / 1e9) = 100
      // FunctionalRateGenerator adds +1 for the "next" event → 101 total fire times
      counter.fireTimes = 0;
      long nextFireTime = generator.computeNextFireTime(10_000_000L, counter);
      assertEquals(101, counter.fireTimes, "10ms at 10,000/sec should yield 101 fire times (100 past + 1 next)");

      // The next fire time should be just past the 10ms mark
      assertTrue(nextFireTime > 10_000_000L, "Next fire time should be after 10ms");
      assertTrue(nextFireTime <= 10_200_000L, "Next fire time should be close to 10ms (within 0.2ms)");
   }

   /**
    * For a ramp rate that ramps from 1,000 to 100,000 users/sec over 10 seconds,
    * verify that fire times are properly sub-millisecond-spaced during the high-rate portion.
    */
   @Test
   public void rampRateHighRatePortionHasSubMillisecondSpacing() {
      final long durationNs = 10_000_000_000L; // 10 seconds
      final RateGenerator generator = RateGenerator.rampRate(1_000, 100_000, durationNs);
      final FireTimesCounter counter = new FireTimesCounter();

      // Jump to 9 seconds (near the end where rate ≈ 91,000 users/sec)
      // inter-arrival ≈ 1_000_000_000 / 91_000 ≈ 10,989 ns ≈ 11 µs
      counter.fireTimes = 0;
      generator.computeNextFireTime(9_000_000_000L, counter);

      // Now step through individual fire times near the high-rate end
      long[] highRateFireTimes = new long[50];
      for (int i = 0; i < highRateFireTimes.length; i++) {
         counter.fireTimes = 0;
         long elapsed = generator.lastComputedFireTimeNs();
         highRateFireTimes[i] = generator.computeNextFireTime(elapsed, counter);
         assertEquals(1, counter.fireTimes, "Each step should produce exactly one fire time");
      }

      // Verify all inter-arrival times are sub-millisecond (< 1,000,000 ns)
      // At ~91,000 users/sec, expected ~11 µs spacing
      for (int i = 1; i < highRateFireTimes.length; i++) {
         long interArrival = highRateFireTimes[i] - highRateFireTimes[i - 1];
         assertTrue(interArrival > 0, "Fire times must be strictly increasing at index " + i);
         assertTrue(interArrival < 1_000_000,
               "Inter-arrival at high rate should be sub-millisecond, was " + interArrival + " ns at index " + i);
      }
   }

   /**
    * For Poisson constant rate at high rates, verify that fire times are
    * sub-millisecond-spaced on average (not bunched at ms boundaries).
    */
   @Test
   public void poissonConstantRateHighRateSubMillisecondSpacing() {
      final double rate = 10_000; // 10,000 users/sec → mean inter-arrival = 100 µs
      final RateGenerator generator = RateGenerator.poissonConstantRate(new Random(42), rate);
      final FireTimesCounter counter = new FireTimesCounter();

      final int samples = 1000;
      final long[] fireTimes = new long[samples];

      for (int i = 0; i < samples; i++) {
         counter.fireTimes = 0;
         long elapsed = generator.lastComputedFireTimeNs();
         fireTimes[i] = generator.computeNextFireTime(elapsed, counter);
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

      // Mean should be close to 100,000 ns (100 µs) — allow 30% tolerance for randomness
      assertTrue(meanInterArrival > 70_000 && meanInterArrival < 130_000,
            "Mean inter-arrival should be ~100,000 ns, was " + meanInterArrival);

      // Most inter-arrivals should be sub-millisecond
      // For exponential distribution with mean 100µs, P(X < 1ms) ≈ 1 - e^(-10) ≈ 99.995%
      assertTrue(subMillisecondCount > samples * 0.99,
            "At least 99% of inter-arrivals should be sub-millisecond, was " + subMillisecondCount + "/" + (samples - 1));
   }

   /**
    * Verify that the total number of fire times produced over a given duration
    * matches the expected count for the configured rate.
    */
   @Test
   public void constantRateTotalFireTimesAccuracy() {
      final double rate = 5_000; // 5,000 users/sec
      final long durationNs = 1_000_000_000L; // 1 second
      final RateGenerator generator = RateGenerator.constantRate(rate);
      final FireTimesCounter counter = new FireTimesCounter();

      // computeFireTimes(1_000_000_000) = (long)(1e9 * 5000 / 1e9) = 5000
      // FunctionalRateGenerator adds +1 for the "next" event → 5001 total
      counter.fireTimes = 0;
      generator.computeNextFireTime(durationNs, counter);

      assertEquals(5_001, counter.fireTimes,
            "1 second at 5,000/sec should produce 5,001 fire times (5,000 past + 1 next)");
   }

   /**
    * Verify that Poisson ramp rate at high rates produces sub-millisecond spacing
    * during the high-rate portion (not bursty).
    */
   @Test
   public void poissonRampRateHighRateNoMillisecondBursts() {
      final long durationNs = 10_000_000_000L; // 10 seconds
      final RateGenerator generator = RateGenerator.poissonRampRate(
            new Random(42), 1_000, 50_000, durationNs);
      final FireTimesCounter counter = new FireTimesCounter();

      // Jump to 9 seconds to get near the high rate (~45,100 users/sec)
      counter.fireTimes = 0;
      generator.computeNextFireTime(9_000_000_000L, counter);

      // Step through individual fire times
      long[] highRateFireTimes = new long[100];
      for (int i = 0; i < highRateFireTimes.length; i++) {
         counter.fireTimes = 0;
         long elapsed = generator.lastComputedFireTimeNs();
         highRateFireTimes[i] = generator.computeNextFireTime(elapsed, counter);
      }

      // At ~45,000 users/sec, mean inter-arrival ≈ 22 µs
      int subMillisecondCount = 0;
      for (int i = 1; i < highRateFireTimes.length; i++) {
         long interArrival = highRateFireTimes[i] - highRateFireTimes[i - 1];
         assertTrue(interArrival > 0, "Fire times must be strictly increasing at index " + i);
         if (interArrival < 1_000_000) {
            subMillisecondCount++;
         }
      }

      // At this rate, virtually all inter-arrivals should be sub-millisecond
      assertTrue(subMillisecondCount > 90,
            "At ~45,000 users/sec most inter-arrivals should be sub-ms, was " + subMillisecondCount + "/99");
   }
}
