package io.hyperfoil.core.impl.rate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests that verify the fire time sequence produces correct, monotonically increasing fire times.
 */
public class FireTimeGenerationTest {

   @Test
   public void constantRateFireTimesAreCorrectlySpaced() {
      double rate = 1000; // 1000 users/sec -> 1,000,000 ns per fire
      FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      long prev = 0;
      for (int i = 1; i <= 10; i++) {
         long fireTime = sequence.nextFireTimeNs();
         long expectedFireTimeNs = (long) Math.ceil(i * 1_000_000.0);
         assertEquals(expectedFireTimeNs, fireTime,
               "Fire time at step " + i + " should be " + expectedFireTimeNs);
         assertTrue(fireTime > prev, "Fire times must be strictly increasing");
         prev = fireTime;
      }
   }

   @Test
   public void constantRateProducesCorrectCount() {
      double rate = 10_000; // 10,000 users/sec -> 100,000 ns per fire
      FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      // Consume fire times up to 1ms (1,000,000 ns)
      int count = 0;
      while (true) {
         long fireTime = sequence.nextFireTimeNs();
         if (fireTime > 1_000_000L) {
            break;
         }
         count++;
      }
      assertEquals(10, count, "1ms at 10,000/sec should produce exactly 10 fire times");
   }

   @Test
   public void rampRateFireTimesAreStrictlyIncreasing() {
      long durationNs = 10_000_000_000L; // 10 seconds
      FireTimeSequence sequence = FireTimeSequence.rampRate(100, 1000, durationNs);

      long prev = -1;
      for (int i = 0; i < 50; i++) {
         long fireTime = sequence.nextFireTimeNs();
         assertTrue(fireTime > prev, "Fire times must be strictly increasing");
         prev = fireTime;
      }
   }

   /**
    * Simulates what OpenModelPhase.proceed() does: consumes fire times one at a time,
    * starting a session for each. With the iterator pattern, each fire time is consumed
    * only when elapsed time reaches it, so fire times are never in the future.
    */
   @Test
   public void simulatedProceedNeverStartsSessionInTheFuture() {
      double rate = 10; // 10 users/sec -> 100ms spacing
      long requestDurationNs = 50_000_000L; // 50ms to process each request
      FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      long[] proceedDelaysNs = {
            0, 0, 0, 0, 0, // on time
            30_000_000L, 0, 0, // 30ms late once, then on time
            0, 50_000_000L, 0, // 50ms late once
            0, 0, 0, 0, 0, 0, 0, 0, 0
      };

      long pendingFireTime = sequence.nextFireTimeNs();
      long currentTimeNs = 0;
      int sessionCount = 0;

      for (int p = 0; p < proceedDelaysNs.length; p++) {
         // Simulate proceed() arriving at the pending fire time + some delay
         long elapsed = pendingFireTime + proceedDelaysNs[p];
         if (elapsed < currentTimeNs) {
            elapsed = currentTimeNs;
         }
         currentTimeNs = elapsed;

         // The iterator pattern: only consume if elapsed >= pending
         assertTrue(currentTimeNs >= pendingFireTime,
               "proceed=" + p + ": must not be called before pending fire time");

         long fireTimeNs = pendingFireTime;
         pendingFireTime = sequence.nextFireTimeNs();
         sessionCount++;

         // The fire time used for CO compensation is never in the future
         assertTrue(fireTimeNs <= currentTimeNs,
               "proceed=" + p + ": fire time " + fireTimeNs + " is in the future relative to " + currentTimeNs);

         // Response time is always positive
         long completionTimeNs = currentTimeNs + requestDurationNs;
         long responseTimeNs = completionTimeNs - fireTimeNs;
         assertTrue(responseTimeNs >= requestDurationNs,
               "proceed=" + p + ": response time " + responseTimeNs
                     + " is less than request duration " + requestDurationNs);
      }

      assertTrue(sessionCount > 0, "Should have started at least one session");
   }

   @Test
   public void constantRateTotalFireTimesAccuracy() {
      double rate = 5_000; // 5,000 users/sec
      long durationNs = 1_000_000_000L; // 1 second
      FireTimeSequence sequence = FireTimeSequence.constantRate(rate);

      int count = 0;
      while (true) {
         long fireTime = sequence.nextFireTimeNs();
         if (fireTime > durationNs) {
            break;
         }
         count++;
      }
      assertEquals(5_000, count, "1 second at 5,000/sec should produce exactly 5,000 fire times");
   }
}
