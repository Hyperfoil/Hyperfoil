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

      // First call at elapsed=0: no past fire times, listener gets nothing
      generator.computeNextFireTime(generator.lastComputedFireTimeNs(), listener);
      assertEquals(0, listener.fireTimes.size(), "First call at elapsed=0 should produce no fire times");

      // Subsequent calls: each should produce exactly one fire time matching the elapsed time
      for (int i = 1; i <= 10; i++) {
         listener.fireTimes.clear();
         long elapsed = generator.lastComputedFireTimeNs();
         generator.computeNextFireTime(elapsed, listener);
         assertEquals(1, listener.fireTimes.size(), "Each subsequent call should produce exactly one fire time");

         long expectedFireTimeNs = (long) Math.ceil(i * 1_000_000.0);
         assertEquals(expectedFireTimeNs, listener.fireTimes.get(0),
               "Fire time at step " + i + " should match the elapsed time, not one interval ahead");
      }
   }

   @Test
   public void constantRateCatchUpPropagatesAllMissingFireTimes() {
      double rate = 10_000; // 10,000 users/sec → 100,000 ns per fire
      RateGenerator generator = RateGenerator.constantRate(rate);
      RecordingListener listener = new RecordingListener();

      // Jump 1ms ahead — should generate exactly 10 past fire times
      generator.computeNextFireTime(1_000_000L, listener);

      assertEquals(10, listener.fireTimes.size(),
            "Jumping 1ms at 10k/sec should produce exactly 10 fire times");

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

   /**
    * The core invariant: no fire time reported to the listener should be in the future
    * relative to the elapsed time passed to computeNextFireTime().
    * <p>
    * Before the fix, this.fireTimes was inflated by +1 from the previous call, so
    * computeFireTimeNs(this.fireTimes + i + 1) computed indices one position ahead —
    * every fire time was one inter-arrival interval in the future. With
    * compensateInternalLatency enabled, that future timestamp becomes the session's
    * startTimestampNanos, making responseTime = completionTime - futureStart negative.
    */
   @Test
   public void fireTimesAreNeverInTheFuture() {
      for (double rate : new double[] { 100, 1_000, 10_000, 100_000 }) {
         RateGenerator generator = RateGenerator.constantRate(rate);
         RecordingListener listener = new RecordingListener();

         for (int i = 0; i < 200; i++) {
            listener.fireTimes.clear();
            long elapsed = generator.lastComputedFireTimeNs();
            generator.computeNextFireTime(elapsed, listener);

            for (int j = 0; j < listener.fireTimes.size(); j++) {
               assertTrue(listener.fireTimes.get(j) <= elapsed,
                     "rate=" + rate + " step=" + i + " fire[" + j + "]=" + listener.fireTimes.get(j)
                           + " > elapsed=" + elapsed + " — fire time is in the future");
            }
         }
      }
   }

   /**
    * Same invariant for ramp rate, which uses a different computeFireTimeNs formula.
    */
   @Test
   public void rampRateFireTimesAreNeverInTheFuture() {
      long durationNs = 10_000_000_000L;
      RateGenerator generator = RateGenerator.rampRate(100, 10_000, durationNs);
      RecordingListener listener = new RecordingListener();

      for (int i = 0; i < 200; i++) {
         listener.fireTimes.clear();
         long elapsed = generator.lastComputedFireTimeNs();
         generator.computeNextFireTime(elapsed, listener);

         for (int j = 0; j < listener.fireTimes.size(); j++) {
            assertTrue(listener.fireTimes.get(j) <= elapsed,
                  "step=" + i + " fire[" + j + "]=" + listener.fireTimes.get(j)
                        + " > elapsed=" + elapsed + " — fire time is in the future");
         }
      }
   }

   /**
    * Simulates what OpenModelPhase.proceed() does: calls computeNextFireTime with the
    * current elapsed time, receives fire times via the listener, starts sessions stamped
    * with those fire times, then a request completes some time later.
    * <p>
    * With compensateInternalLatency, response time = completionNanos - fireTimeNs.
    * If fireTimeNs is in the future (beyond elapsed), the response time is negative.
    * <p>
    * Before the fix, the inflated this.fireTimes shifted every fire time index forward
    * by one inter-arrival interval, making every response time negative — even when
    * proceed() arrives exactly on time.
    */
   @Test
   public void compensatedResponseTimesAreNeverNegative() {
      double rate = 10; // 10 users/sec → 100ms spacing
      long requestDurationNs = 50_000_000L; // 50ms to process each request
      RateGenerator generator = RateGenerator.constantRate(rate);
      RecordingListener listener = new RecordingListener();

      // Simulate 20 proceed() calls, some on time, some late
      long[] proceedDelaysNs = {
            0, 0, 0, 0, 0, // on time
            30_000_000L, 0, 0, // 30ms late once, then on time
            0, 50_000_000L, 0, // 50ms late once
            0, 0, 0, 0, 0, 0, 0, 0, 0
      };

      long currentTimeNs = 0;
      long nextScheduledFireTimeNs = generator.lastComputedFireTimeNs();
      int sessionCount = 0;

      for (int p = 0; p < proceedDelaysNs.length; p++) {
         // Simulate proceed() arriving at the scheduled fire time + some delay,
         // mirroring OpenModelPhase.proceed() which wakes up at the scheduled time
         long elapsed = nextScheduledFireTimeNs + proceedDelaysNs[p];
         if (elapsed < currentTimeNs) {
            elapsed = currentTimeNs; // time doesn't go backwards
         }
         currentTimeNs = elapsed;

         listener.fireTimes.clear();
         // computeNextFireTime returns the next scheduled fire time,
         // which OpenModelPhase uses to schedule the next proceed() call
         nextScheduledFireTimeNs = generator.computeNextFireTime(elapsed, listener);
         assertTrue(nextScheduledFireTimeNs > elapsed,
               "proceed=" + p + ": next fire time " + nextScheduledFireTimeNs
                     + " must be strictly after elapsed " + elapsed);

         // For each session that was started, simulate a request completing
         for (Long fireTimeNs : listener.fireTimes) {
            sessionCount++;
            // The request completes requestDurationNs after the proceed() call
            long completionTimeNs = currentTimeNs + requestDurationNs;
            // With compensateInternalLatency: responseTime = completion - fireTime
            long responseTimeNs = completionTimeNs - fireTimeNs;
            assertTrue(responseTimeNs >= 0,
                  "proceed=" + p + " session=" + sessionCount
                        + ": negative response time! elapsed=" + currentTimeNs
                        + " fireTime=" + fireTimeNs + " completion=" + completionTimeNs
                        + " responseTime=" + responseTimeNs);
            // Response time should also be at least the request duration
            assertTrue(responseTimeNs >= requestDurationNs,
                  "proceed=" + p + " session=" + sessionCount
                        + ": response time " + responseTimeNs
                        + " is less than request duration " + requestDurationNs);
         }
      }

      assertTrue(sessionCount > 0, "Should have started at least one session");
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
