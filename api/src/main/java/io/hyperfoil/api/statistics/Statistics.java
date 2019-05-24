package io.hyperfoil.api.statistics;

import java.util.function.Supplier;

import org.HdrHistogram.SingleWriterRecorder;
import org.HdrHistogram.WriterReaderPhaser;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * This is a copy/subset of {@link SingleWriterRecorder} but uses {@link StatisticsSnapshot} instead of only
 * the histogram.
 */
public class Statistics {
   private static final Logger log = LoggerFactory.getLogger(Statistics.class);
   private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

   private volatile StatisticsSnapshot active;
   private StatisticsSnapshot inactive;
   private boolean stopped;

   public Statistics(long startTimestamp) {
      active = new StatisticsSnapshot();
      inactive = new StatisticsSnapshot();
      active.histogram.setStartTimeStamp(startTimestamp);
   }

   public void recordResponse(long sendTime, long responseTime) {
      long highestTrackableValue = active.histogram.getHighestTrackableValue();
      if (responseTime > highestTrackableValue) {
         // we don't use auto-resize histograms
         log.warn("Response time {} exceeded maximum trackable response time {}", responseTime, highestTrackableValue);
         responseTime = highestTrackableValue;
      }
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.histogram.recordValue(responseTime);
         active.totalSendTime += sendTime;
         active.responseCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementRequests() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.requestCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementTimeouts() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.timeouts++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementResets() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.resetCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementBlockedCount() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.blockedCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementBlockedTime(long blockedTime) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.blockedTime += blockedTime;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void addStatus(int code) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         switch (code / 100) {
            case 2:
               active.status_2xx++;
               break;
            case 3:
               active.status_3xx++;
               break;
            case 4:
               active.status_4xx++;
               break;
            case 5:
               active.status_5xx++;
               break;
            default:
               active.status_other++;
         }
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   @SuppressWarnings("unchecked")
   public <T extends CustomValue> T getCustom(Object key, Supplier<T> identitySupplier) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         CustomValue custom = active.custom.get(key);
         if (custom == null) {
            custom = identitySupplier.get();
            active.custom.put(key, custom);
         }
         return (T) custom;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void addInvalid() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.invalid++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public synchronized void moveIntervalTo(StatisticsSnapshot target) {
      performIntervalSample();
      inactive.copyInto(target);
   }

   public synchronized void addIntervalTo(StatisticsSnapshot target) {
      performIntervalSample();
      inactive.addInto(target);
   }

   public synchronized void start(long now) {
      // we can modify start timestamp because writers are not accessing that
      // and readers are blocked by synchronized section
      active.histogram.setStartTimeStamp(now);
      stopped = false;
   }

   public synchronized void end(long now) {
      // we can modify end timestamp because writers are not accessing that
      // and readers are blocked by synchronized section
      active.histogram.setEndTimeStamp(now);
      stopped = true;
   }

   private void performIntervalSample() {
      try {
         recordingPhaser.readerLock();

         // Make sure we have an inactive version to flip in:
         if (inactive == null) {
            inactive = new StatisticsSnapshot();
         }

         inactive.reset();

         // Swap active and inactive histograms:
         final StatisticsSnapshot tempHistogram = inactive;
         inactive = active;
         active = tempHistogram;

         // Mark end time of previous interval and start time of new one:
         long now = System.currentTimeMillis();
         active.histogram.setStartTimeStamp(now);
         if (!stopped) {
            inactive.histogram.setEndTimeStamp(now);
         }

         // Make sure we are not in the middle of recording a value on the previously active histogram:

         // Flip phase to make sure no recordings that were in flight pre-flip are still active:
         recordingPhaser.flipPhase(500000L /* yield in 0.5 msec units if needed */);
      } finally {
         recordingPhaser.readerUnlock();
      }
   }

   public StatisticsSnapshot snapshot() {
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      addIntervalTo(snapshot);
      return snapshot;
   }
}
