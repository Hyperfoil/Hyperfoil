package io.hyperfoil.api.config;

import io.hyperfoil.api.session.Session;

/**
 * Marker (tagging interface) providing shared timestamp logic across steps.
 */
public interface StartTimeSource {

   /**
    * Populates the provided buffer with the start timestamp in milliseconds (index 0)
    * and nanoseconds (index 1).
    * <p>
    * Both millisecond and nanosecond values are captured/retrieved together to ensure
    * temporal consistency between the two units. Accepts a mutable {@code target} array
    * to avoid object allocations on the heap, eliminating Garbage Collection (GC)
    * pressure in high-throughput paths.
    *
    * @param session the session instance to read scheduled start times from
    * @param useSessionStartTime whether to attempt using the session's scheduled timing
    * @param target a pre-allocated {@code long[]} buffer to store [millis, nanos]
    */
   default void createStartTimestamp(Session session, boolean useSessionStartTime, long[] target) {
      assert target.length == 2;
      if (useSessionStartTime) {
         long sessionStartTime = session.scheduledStartTimestamp();
         long sessionStartNanoTime = session.scheduledStartNanoTime();

         if (sessionStartTime != -1 && sessionStartNanoTime != -1) {
            target[0] = sessionStartTime;
            target[1] = sessionStartNanoTime;
            return;
         }
      }
      target[0] = System.currentTimeMillis();
      target[1] = System.nanoTime();
   }

   long getStartTimestampMillis(Session session);

   long getStartTimestampNanos(Session session);

   /**
    * Gets the timestamp when the operation actually fired.
    * This decides which time bucket the statistics are saved in.
    *
    * @param session the session owning the operation
    * @return milliseconds since the epoch, or 0 if it hasn't fired yet
    */
   long getFiredTimestampMillis(Session session);

   /**
    * Sets the timestamp when the operation fired.
    *
    * IMPORTANT: Call this BEFORE recording any statistics (like blocked time).
    * This ensures all stats for this request end up in the exact same time bucket.
    *
    * @param session the session owning the operation
    */
   void setFiredTimestampMillis(Session session);
}
