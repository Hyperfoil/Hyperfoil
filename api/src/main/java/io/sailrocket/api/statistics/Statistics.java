package io.sailrocket.api.statistics;

import org.HdrHistogram.SingleWriterRecorder;
import org.HdrHistogram.WriterReaderPhaser;

/**
 * This is a copy/subset of {@link SingleWriterRecorder} but uses {@link StatisticsSnapshot} instead of only
 * the histogram.
 */
public class Statistics {
   private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();

   private volatile StatisticsSnapshot active;
   private StatisticsSnapshot inactive;

   public Statistics() {
      active = new StatisticsSnapshot();
      inactive = new StatisticsSnapshot();
      active.histogram.setStartTimeStamp(System.currentTimeMillis());
   }

   public void recordValue(final long value) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.histogram.recordValue(value);
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

   public void incrementResponses() {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         active.responseCount++;
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

   public synchronized void moveIntervalTo(StatisticsSnapshot target) {
      performIntervalSample();
      inactive.copyInto(target);
   }

   public synchronized void addIntervalTo(StatisticsSnapshot target) {
      performIntervalSample();
      inactive.addInto(target);
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
         inactive.histogram.setEndTimeStamp(now);

         // Make sure we are not in the middle of recording a value on the previously active histogram:

         // Flip phase to make sure no recordings that were in flight pre-flip are still active:
         recordingPhaser.flipPhase(500000L /* yield in 0.5 msec units if needed */);
      } finally {
         recordingPhaser.readerUnlock();
      }
   }
}
