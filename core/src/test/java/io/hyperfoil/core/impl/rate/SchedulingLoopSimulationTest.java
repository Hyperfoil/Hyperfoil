package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that simulate the {@code OpenModelPhase.proceed()} scheduling loop using a virtual clock.
 * <p>
 * The scheduling loop pattern is:
 * <ol>
 * <li>Compute elapsed time (virtual clock)</li>
 * <li>Call {@code generator.computeNextFireTime(elapsedTimeNs, listener)}</li>
 * <li>Compute scheduled delay = max(0, (expectedNext - elapsedTimeNs) - computationDelayNs)</li>
 * <li>Advance virtual clock by scheduledDelay + jitterNs</li>
 * </ol>
 * This mirrors the real scheduling loop without depending on real time or event loops.
 */
public class SchedulingLoopSimulationTest {

   record SchedulingLoopResult(
         long[] scheduledDelaysNs,
         long[] fireTimesPerIteration,
         long totalFireTimes,
         int catchUpCount) {
   }

   private static SchedulingLoopResult simulateSchedulingLoop(
         RateGenerator generator,
         int iterations,
         long computationDelayNs,
         long jitterNs,
         long initialElapsedNs) {

      long[] scheduledDelays = new long[iterations];
      long[] fireTimesPerIteration = new long[iterations];
      long totalFireTimes = 0;
      int catchUpCount = 0;

      FireTimesCounter counter = new FireTimesCounter();
      long virtualNow = initialElapsedNs;

      for (int i = 0; i < iterations; i++) {
         counter.fireTimes = 0;
         long expectedNext = generator.computeNextFireTime(virtualNow, counter);
         long firedThisIteration = counter.fireTimes;
         fireTimesPerIteration[i] = firedThisIteration;
         totalFireTimes += firedThisIteration;

         if (firedThisIteration > 1) {
            catchUpCount++;
         }

         long scheduledDelay = Math.max(0, (expectedNext - virtualNow) - computationDelayNs);
         scheduledDelays[i] = scheduledDelay;

         virtualNow += scheduledDelay + computationDelayNs + jitterNs;
      }

      return new SchedulingLoopResult(scheduledDelays, fireTimesPerIteration, totalFireTimes, catchUpCount);
   }

   static Stream<Arguments> subMillisecondGenerators() {
      return Stream.of(
            Arguments.of("constantRate", RateGenerator.constantRate(10_000)),
            Arguments.of("rampRate", RateGenerator.rampRate(10_000, 10_000, 10_000_000_000L)),
            Arguments.of("poissonConstantRate", RateGenerator.poissonConstantRate(new Random(42), 10_000)),
            Arguments.of("poissonRampRate", RateGenerator.poissonRampRate(new Random(42), 10_000, 10_000, 10_000_000_000L)));
   }

   /**
    * At 10,000 users/sec (100µs period), all generator types should produce sub-millisecond
    * scheduled delays when there is no computation delay or jitter.
    */
   @ParameterizedTest(name = "{0}")
   @MethodSource("subMillisecondGenerators")
   public void scheduledDelaysAreSubMillisecondAtHighRate(String name, RateGenerator generator) {
      int iterations = 500;
      SchedulingLoopResult result = simulateSchedulingLoop(generator, iterations, 0, 0, 0);

      // All delays should be sub-millisecond
      for (int i = 0; i < iterations; i++) {
         assertTrue(result.scheduledDelaysNs[i] < 1_000_000,
               name + ": delay at iteration " + i + " was " + result.scheduledDelaysNs[i] + " ns, expected < 1ms");
      }

      // For deterministic generators: exactly 1 fire per iteration
      // For Poisson: statistically ~1 fire per iteration (mean should be close to 1)
      if (name.startsWith("poisson")) {
         double meanFires = (double) result.totalFireTimes / iterations;
         assertTrue(meanFires > 0.8 && meanFires < 1.2,
               name + ": mean fires per iteration should be ~1, was " + meanFires);
      } else {
         for (int i = 0; i < iterations; i++) {
            assertEquals(1, result.fireTimesPerIteration[i],
                  name + ": expected 1 fire at iteration " + i + ", got " + result.fireTimesPerIteration[i]);
         }
      }
   }

   /**
    * When computation delay (50µs) is less than the fire period (100µs at 10k/sec),
    * the scheduled delay should be reduced by the computation delay, and no catch-ups should occur.
    */
   @Test
   public void computationDelayReducesScheduledDelay() {
      RateGenerator generator = RateGenerator.constantRate(10_000);
      long computationDelayNs = 50_000; // 50µs
      long expectedPeriodNs = 100_000; // 100µs at 10k/sec

      SchedulingLoopResult result = simulateSchedulingLoop(generator, 200, computationDelayNs, 0, 0);

      assertEquals(0, result.catchUpCount, "No catch-ups expected when computation delay < period");

      // Each scheduled delay should be approximately period - computationDelay = 50µs
      for (int i = 0; i < 200; i++) {
         long expectedDelay = expectedPeriodNs - computationDelayNs;
         assertTrue(result.scheduledDelaysNs[i] >= expectedDelay - 1 && result.scheduledDelaysNs[i] <= expectedDelay + 1,
               "Delay at iteration " + i + " was " + result.scheduledDelaysNs[i] + " ns, expected ~" + expectedDelay);
      }

      for (int i = 0; i < 200; i++) {
         assertEquals(1, result.fireTimesPerIteration[i],
               "Expected 1 fire at iteration " + i);
      }
   }

   /**
    * When computation delay (15µs) exceeds the fire period (10µs at 100k/sec),
    * the generator must catch up: some iterations fire more than 1 event.
    */
   @Test
   public void computationDelayExceedsPeriodCausesCatchUp() {
      RateGenerator generator = RateGenerator.constantRate(100_000); // 10µs period
      long computationDelayNs = 15_000; // 15µs > 10µs period
      int iterations = 500;

      SchedulingLoopResult result = simulateSchedulingLoop(generator, iterations, computationDelayNs, 0, 0);

      // Some catch-ups should occur since computation delay > period
      assertTrue(result.catchUpCount > 0,
            "Expected catch-ups when computation delay exceeds period, got " + result.catchUpCount);

      // Some iterations should fire >1 event
      boolean hasMultiFire = false;
      for (int i = 0; i < iterations; i++) {
         if (result.fireTimesPerIteration[i] > 1) {
            hasMultiFire = true;
            break;
         }
      }
      assertTrue(hasMultiFire, "Expected some iterations to fire >1 event during catch-up");

      // Total fire times should be consistent with elapsed time
      // With 15µs computation delay per iteration + 0 scheduled delay (clamped to 0),
      // total elapsed = iterations * 15µs, expected fires ≈ elapsed * rate
      long totalElapsedNs = iterations * computationDelayNs;
      long expectedFires = (long) (totalElapsedNs * 100_000.0 / 1_000_000_000.0);
      // Allow some tolerance due to ceiling/rounding
      assertTrue(Math.abs(result.totalFireTimes - expectedFires) <= iterations,
            "Total fires " + result.totalFireTimes + " should be close to " + expectedFires);
   }

   /**
    * Small scheduling jitter (5µs) at 10k/sec (100µs period) should not cause catch-ups
    * or bursts. Each iteration should fire exactly 1 event.
    */
   @Test
   public void schedulingJitterDoesNotCauseBursts() {
      RateGenerator generator = RateGenerator.constantRate(10_000);
      long jitterNs = 5_000; // 5µs jitter, well under 100µs period
      int iterations = 300;

      SchedulingLoopResult result = simulateSchedulingLoop(generator, iterations, 0, jitterNs, 0);

      assertEquals(0, result.catchUpCount,
            "No catch-ups expected with small jitter, got " + result.catchUpCount);

      for (int i = 0; i < iterations; i++) {
         assertEquals(1, result.fireTimesPerIteration[i],
               "Expected 1 fire at iteration " + i);
      }

      // All delays should remain positive
      for (int i = 0; i < iterations; i++) {
         assertTrue(result.scheduledDelaysNs[i] > 0,
               "Delay at iteration " + i + " should be positive, was " + result.scheduledDelaysNs[i]);
      }
   }

   /**
    * When the simulation starts with a large initial elapsed time (10ms),
    * the first iteration should catch up by firing ~100 events (10ms * 10k/sec),
    * and subsequent iterations should stabilize at 1 fire each.
    */
   @Test
   public void delayedStartCatchesUpThenStabilizes() {
      RateGenerator generator = RateGenerator.constantRate(10_000);
      long initialElapsedNs = 10_000_000; // 10ms
      int iterations = 200;

      SchedulingLoopResult result = simulateSchedulingLoop(generator, iterations, 0, 0, initialElapsedNs);

      // First iteration should catch up with many fires
      // 10ms * 10k/sec = 100 events, +1 for the next = 101
      assertTrue(result.fireTimesPerIteration[0] >= 100,
            "First iteration should catch up with ~100+ fires, got " + result.fireTimesPerIteration[0]);

      // Subsequent iterations should stabilize at 1 fire each
      int stableCount = 0;
      for (int i = 1; i < iterations; i++) {
         if (result.fireTimesPerIteration[i] == 1) {
            stableCount++;
         }
      }
      assertTrue(stableCount >= (iterations - 1) * 0.95,
            "At least 95% of subsequent iterations should fire exactly 1 event, got " + stableCount + "/" + (iterations - 1));
   }

   /**
    * A ramp rate from 1k to 100k users/sec over 10 seconds should produce
    * decreasing scheduled delays: early delays ~1ms, late delays ~10µs.
    */
   @Test
   public void rampRateProducesDecreasingScheduledDelays() {
      long durationNs = 10_000_000_000L;
      RateGenerator generator = RateGenerator.rampRate(1_000, 100_000, durationNs);
      int iterations = 5000;

      SchedulingLoopResult result = simulateSchedulingLoop(generator, iterations, 0, 0, 0);

      // Collect delays into early (first 50) and late (last 50) groups
      List<Long> earlyDelays = new ArrayList<>();
      List<Long> lateDelays = new ArrayList<>();

      for (int i = 0; i < 50; i++) {
         earlyDelays.add(result.scheduledDelaysNs[i]);
      }
      for (int i = iterations - 50; i < iterations; i++) {
         lateDelays.add(result.scheduledDelaysNs[i]);
      }

      double earlyMean = earlyDelays.stream().mapToLong(Long::longValue).average().orElse(0);
      double lateMean = lateDelays.stream().mapToLong(Long::longValue).average().orElse(0);

      // Early delays should be around 1ms (1,000,000 ns) at 1k/sec
      assertTrue(earlyMean > 500_000 && earlyMean < 1_500_000,
            "Early mean delay should be ~1ms, was " + earlyMean + " ns");

      // Late delays should be significantly smaller than early delays
      // As the rate ramps toward 100k/sec, delays decrease toward ~10µs
      assertTrue(lateMean < earlyMean / 5,
            "Late mean delay (" + lateMean + ") should be much smaller than early (" + earlyMean + ")");

      // Overall trend: early delays should be significantly larger than late delays
      assertTrue(earlyMean > lateMean * 5,
            "Early delays (" + earlyMean + ") should be at least 5x larger than late delays (" + lateMean + ")");
   }
}
