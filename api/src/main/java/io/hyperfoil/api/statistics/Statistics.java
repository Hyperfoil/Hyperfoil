package io.hyperfoil.api.statistics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.HdrHistogram.SingleWriterRecorder;
import org.HdrHistogram.WriterReaderPhaser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a copy/subset of {@link SingleWriterRecorder} but uses {@link StatisticsSnapshot} instead of only
 * the histogram.
 */
public class Statistics {
   private static final Logger log = LogManager.getLogger(Statistics.class);
   private static final long SAMPLING_PERIOD_MILLIS = TimeUnit.SECONDS.toMillis(1);
   private static final ThreadLocal<Long> lastWarnThrottle = ThreadLocal.withInitial(() -> Long.MIN_VALUE);

   private static final AtomicIntegerFieldUpdater<Statistics> LU1 =
         AtomicIntegerFieldUpdater.newUpdater(Statistics.class, "lowestActive1");
   private static final AtomicIntegerFieldUpdater<Statistics> LU2 =
         AtomicIntegerFieldUpdater.newUpdater(Statistics.class, "lowestActive2");

   private final WriterReaderPhaser recordingPhaser = new WriterReaderPhaser();
   private final long highestTrackableValue;
   // We'll start making space 4 samples (seconds) ahead; in case the readers fall behind the schedule
   // this will help to keep the active array always big enough.
   private int numSamples = 4;

   @SuppressWarnings("unused")
   private volatile int lowestActive1;
   @SuppressWarnings("unused")
   private volatile int lowestActive2;
   private volatile int highestActive;
   @SuppressWarnings("AtomicFieldUpdaterNotStaticFinal")
   private volatile AtomicIntegerFieldUpdater<Statistics> lowestActiveUpdater = LU1;
   private volatile AtomicReferenceArray<StatisticsSnapshot> active;
   private AtomicReferenceArray<StatisticsSnapshot> inactive;

   private long startTimestamp;
   private long endTimestamp = Long.MAX_VALUE;
   private int lastLowestIndex;

   public Statistics(long startTimestamp) {
      this.startTimestamp = startTimestamp;
      active = new AtomicReferenceArray<>(16);
      inactive = new AtomicReferenceArray<>(16);
      StatisticsSnapshot first = new StatisticsSnapshot();
      first.sequenceId = 0;
      active.set(0, first);
      highestTrackableValue = first.histogram.getHighestTrackableValue();
   }

   public void recordResponse(long startTimestamp, long responseTime) {
      if (responseTime > highestTrackableValue) {
         // we don't use auto-resize histograms
         long lastWarn = lastWarnThrottle.get();
         long warnings = lastWarn & 0xFFFF;
         long now = System.currentTimeMillis();
         if (now - (lastWarn >> 16) > 100) {
            log.warn("Response time {} exceeded maximum trackable response time {}",
                  responseTime, highestTrackableValue);
            if (warnings > 0) {
               log.warn("Response time was also exceeded {} times since last warning", warnings);
            }
            lastWarnThrottle.set(now << 16);
         } else if (warnings < 0xFFFF) {
            lastWarnThrottle.set(lastWarn + 1);
         }
         responseTime = highestTrackableValue;
      } else if (responseTime < 0) {
         log.warn("Response time {} is negative.", responseTime);
         responseTime = 0;
      }
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(startTimestamp);
         active.histogram.recordValue(responseTime);
         active.responseCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementRequests(long timestamp) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.requestCount++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementTimeouts(long timestamp) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.requestTimeouts++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementConnectionErrors(long timestamp) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.connectionErrors++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementInternalErrors(long timestamp) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.internalErrors++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void incrementBlockedTime(long timestamp, long blockedTime) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.blockedTime += blockedTime;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public <C extends StatsExtension> void update(String key, long timestamp, Supplier<C> creator, LongUpdater<C> updater, long value) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         StatsExtension custom = active.extensions.get(key);
         if (custom == null) {
            custom = creator.get();
            active.extensions.put(key, custom);
         }
         //noinspection unchecked
         updater.update((C) custom, value);
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public <C extends StatsExtension> void update(String key, long timestamp, Supplier<C> creator, ObjectUpdater<C> updater, Object value) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         StatsExtension custom = active.extensions.get(key);
         if (custom == null) {
            custom = creator.get();
            active.extensions.put(key, custom);
         }
         //noinspection unchecked
         updater.update((C) custom, value);
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void addInvalid(long timestamp) {
      long criticalValueAtEnter = recordingPhaser.writerCriticalSectionEnter();
      try {
         StatisticsSnapshot active = active(timestamp);
         active.invalid++;
      } finally {
         recordingPhaser.writerCriticalSectionExit(criticalValueAtEnter);
      }
   }

   public void visitSnapshots(Consumer<StatisticsSnapshot> consumer) {
      try {
         recordingPhaser.readerLock();

         if (++numSamples >= inactive.length()) {
            AtomicReferenceArray<StatisticsSnapshot> temp = new AtomicReferenceArray<>(inactive.length() * 2);
            for (int i = lastLowestIndex; i < inactive.length(); ++i) {
               temp.set(i, inactive.get(i));
            }
            inactive = temp;
         }

         // Swap active and inactive histograms:
         final AtomicReferenceArray<StatisticsSnapshot> tempHistogram = inactive;
         inactive = active;
         active = tempHistogram;

         AtomicIntegerFieldUpdater<Statistics> inactiveUpdater = lowestActiveUpdater;
         lowestActiveUpdater = inactiveUpdater == LU1 ? LU2 : LU1;

         // Make sure we are not in the middle of recording a value on the previously active histogram:

         // Flip phase to make sure no recordings that were in flight pre-flip are still active:
         recordingPhaser.flipPhase(500000L /* yield in 0.5 msec units if needed */);

         lastLowestIndex = Math.min(LU1.get(this), LU2.get(this));

         int maxSamples;
         // If the statistics is not finished don't publish the last timestamp
         // as this might be shortened be the termination of the phase.
         if (endTimestamp != Long.MAX_VALUE) {
            maxSamples = Math.min(inactive.length(), highestActive + 1);
         } else {
            maxSamples = Math.min(inactive.length() - 1, highestActive);
         }
         // Make sure that few flips later we'll fetch the stats
         inactiveUpdater.set(this, maxSamples);
         publish(inactive, maxSamples, consumer);
         if (endTimestamp != Long.MAX_VALUE) {
            // all requests must be complete, let's scan the 'active' as well
            publish(active, maxSamples, consumer);
         }
      } finally {
         recordingPhaser.readerUnlock();
      }
   }

   private void publish(AtomicReferenceArray<StatisticsSnapshot> array, int limit, Consumer<StatisticsSnapshot> consumer) {
      for (int i = lastLowestIndex; i < limit; ++i) {
         StatisticsSnapshot snapshot = array.get(i);
         if (snapshot == null) {
            // nothing to do
         } else if (snapshot.isEmpty()) {
            array.set(i, null);
         } else {
            snapshot.histogram.setStartTimeStamp(startTimestamp + i * SAMPLING_PERIOD_MILLIS);
            snapshot.histogram.setEndTimeStamp(Math.min(endTimestamp, startTimestamp + (i + 1) * SAMPLING_PERIOD_MILLIS));
            consumer.accept(snapshot);
            snapshot.reset();
         }
      }
   }

   public void start(long now) {
      recordingPhaser.readerLock();
      try {
         startTimestamp = now;
         endTimestamp = Long.MAX_VALUE;
      } finally {
         recordingPhaser.readerUnlock();
      }
   }

   public void end(long now) {
      recordingPhaser.readerLock();
      try {
         endTimestamp = now;
      } finally {
         recordingPhaser.readerUnlock();
      }
   }

   private StatisticsSnapshot active(long timestamp) {
      int index = (int) ((timestamp - startTimestamp) / SAMPLING_PERIOD_MILLIS);
      AtomicReferenceArray<StatisticsSnapshot> active = this.active;
      if (index >= active.length()) {
         index = active.length() - 1;
      } else if (index < 0) {
         log.error("Record start timestamp {} predates statistics start {}", timestamp, startTimestamp);
         index = 0;
      }
      StatisticsSnapshot snapshot = active.get(index);
      if (snapshot == null) {
         snapshot = new StatisticsSnapshot();
         snapshot.sequenceId = index;
         active.set(index, snapshot);
      }
      lowestActiveUpdater.accumulateAndGet(this, index, Math::min);
      // Highest active is increasing monotonically and it is updated only by the event-loop thread;
      // therefore we don't have to use CAS operation
      if (index > highestActive) {
         highestActive = index;
      }
      return snapshot;
   }

   public interface LongUpdater<C extends StatsExtension> {
      void update(C custom, long value);
   }

   public interface ObjectUpdater<C extends StatsExtension> {
      void update(C custom, Object value);
   }
}
