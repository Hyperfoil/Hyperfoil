package io.hyperfoil.api.config;

import io.hyperfoil.api.session.Session;

/**
 * Marker (tagging interface) providing shared timestamp logic across steps.
 */
public interface StartTimeSource {

   default long[] createStartTimestamp(Session session) {
      long sessionStartTime = session.scheduledStartTimestamp();
      long sessionStartNanoTime = session.scheduledStartNanoTime();
      long startTimestampMillis;
      long startTimestampNanos;
      if (sessionStartTime == -1 || sessionStartNanoTime == -1) {
         startTimestampMillis = System.currentTimeMillis();
         startTimestampNanos = System.nanoTime();
      } else {
         startTimestampMillis = sessionStartTime;
         startTimestampNanos = sessionStartNanoTime;
      }
      return new long[] { startTimestampMillis, startTimestampNanos };
   }

   // TODO try to get rid of Session
   long getStartTimestampMillis(Session session);

   // TODO try to get rid of Session
   long getStartTimestampNanos(Session session);
}
