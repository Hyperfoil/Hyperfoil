package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests that verify fire times are correctly propagated through the {@link FireTimeListener} interface.
 * This is essential for the coordinated omission fix: open model phases need to know
 * the <em>intended</em> fire time for each session, not just the count.
 */
public class FireTimeGenerationTest {

   /**
    * A listener that records all fire times it receives.
    */
   static class RecordingListener implements FireTimeListener {
      final List<Long> fireTimes = new ArrayList<>();

      @Override
      public void onFireTime(long fireTimeNs) {
         fireTimes.add(fireTimeNs);
      }
   }

   @Test
   public void constantRateFireTimesAreCorrectlyPropagated() {
      double rate = 1000; // 1000 users/sec → 1,000,000 ns per fire
      RateGenerator generator = RateGenerator.constantRate(rate);
      RecordingListener listener = new RecordingListener();

      // Step through one fire at a time
      for (int i = 0; i < 10; i++) {
         listener.fireTimes.clear();
         long elapsed = generator.lastComputedFireTimeNs();
         generator.computeNextFireTime(elapsed, listener);
         assertEquals(1, listener.fireTimes.size(), "Each call should produce exactly one fire time");

         long expectedFireTimeNs = (long) Math.ceil((i + 1) * 1_000_000.0);
         assertEquals(expectedFireTimeNs, listener.fireTimes.get(0),
               "Fire time at step " + i + " should match the expected scheduled time");
      }
   }

   @Test
   public void constantRateCatchUpPropagatesAllMissingFireTimes() {
      double rate = 10_000; // 10,000 users/sec → 100,000 ns per fire
      RateGenerator generator = RateGenerator.constantRate(rate);
      RecordingListener listener = new RecordingListener();

      // Jump 1ms ahead — should generate ~10 fire times
      generator.computeNextFireTime(1_000_000L, listener);

      // We expect 11 fire times (10 missed + 1 next)
      assertEquals(11, listener.fireTimes.size(),
            "Jumping 1ms at 10k/sec should produce 11 fire times");

      // All fire times should be strictly increasing
      for (int i = 1; i < listener.fireTimes.size(); i++) {
         assertTrue(listener.fireTimes.get(i) > listener.fireTimes.get(i - 1),
               "Fire times must be strictly increasing at index " + i);
      }

      // Fire times should be evenly spaced at ~100,000 ns
      for (int i = 1; i < listener.fireTimes.size(); i++) {
         long gap = listener.fireTimes.get(i) - listener.fireTimes.get(i - 1);
         assertEquals(100_000L, gap, 1,
               "Gap between fire times should be ~100,000 ns at index " + i);
      }
   }

   @Test
   public void sequentialGeneratorPropagatesFireTimes() {
      // Poisson constant rate uses SequentialRateGenerator
      double rate = 1000;
      RateGenerator generator = RateGenerator.poissonConstantRate(rate);
      RecordingListener listener = new RecordingListener();

      // Step through fire times one at a time
      for (int i = 0; i < 20; i++) {
         listener.fireTimes.clear();
         long elapsed = generator.lastComputedFireTimeNs();
         generator.computeNextFireTime(elapsed, listener);
         assertEquals(1, listener.fireTimes.size());
         assertTrue(listener.fireTimes.get(0) > 0,
               "Fire time should be positive at step " + i);
      }
   }

   @Test
   public void rampRateFireTimesAreStrictlyIncreasing() {
      long durationNs = 10_000_000_000L; // 10 seconds
      RateGenerator generator = RateGenerator.rampRate(100, 1000, durationNs);
      RecordingListener listener = new RecordingListener();

      long prevFireTime = -1;
      for (int i = 0; i < 50; i++) {
         listener.fireTimes.clear();
         long elapsed = generator.lastComputedFireTimeNs();
         generator.computeNextFireTime(elapsed, listener);

         for (Long ft : listener.fireTimes) {
            assertTrue(ft > prevFireTime, "Fire times must be strictly increasing");
            prevFireTime = ft;
         }
      }
   }

}
