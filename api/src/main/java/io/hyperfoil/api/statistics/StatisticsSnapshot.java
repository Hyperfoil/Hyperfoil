package io.hyperfoil.api.statistics;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

import org.HdrHistogram.Histogram;

/**
 * Non-thread safe mutable set of values.
 */
public class StatisticsSnapshot implements Serializable {
   public int sequenceId = -1;
   public final Histogram histogram = new Histogram(TimeUnit.MINUTES.toNanos(1), 2);
   public long totalSendTime;
   public int connectFailureCount;
   public int requestCount;
   public int responseCount;
   public int status_2xx;
   public int status_3xx;
   public int status_4xx;
   public int status_5xx;
   public int status_other;
   public int invalid;
   public int cacheHits;
   public int resetCount;
   public int timeouts;
   public int internalErrors;
   public int blockedCount;
   public long blockedTime;
   public final Map<Object, CustomValue> custom = new HashMap<>();

   public int[] statuses() {
      return new int[]{ status_2xx, status_3xx, status_4xx, status_5xx, status_other };
   }

   public boolean isEmpty() {
      return connectFailureCount + requestCount + responseCount +
            status_2xx + status_3xx + status_4xx + status_5xx + status_other +
            invalid + cacheHits + resetCount + timeouts + internalErrors + blockedCount == 0 &&
            custom.values().stream().allMatch(CustomValue::isNull);
   }

   public void reset() {
      histogram.reset();
      totalSendTime = 0;
      connectFailureCount = 0;
      requestCount = 0;
      responseCount = 0;
      status_2xx = 0;
      status_3xx = 0;
      status_4xx = 0;
      status_5xx = 0;
      status_other = 0;
      invalid = 0;
      cacheHits = 0;
      resetCount = 0;
      timeouts = 0;
      internalErrors = 0;
      blockedCount = 0;
      blockedTime = 0;
      for (CustomValue value : custom.values()) {
         if (value != null) {
            value.reset();
         }
      }
   }

   public StatisticsSnapshot clone() {
      StatisticsSnapshot copy = new StatisticsSnapshot();
      copyInto(copy);
      return copy;
   }

   public void copyInto(StatisticsSnapshot target) {
      copySequenceId(target);
      histogram.copyInto(target.histogram);
      target.totalSendTime = totalSendTime;
      target.connectFailureCount = connectFailureCount;
      target.requestCount = requestCount;
      target.responseCount = responseCount;
      target.status_2xx = status_2xx;
      target.status_3xx = status_3xx;
      target.status_4xx = status_4xx;
      target.status_5xx = status_5xx;
      target.status_other = status_other;
      target.invalid = invalid;
      target.cacheHits = cacheHits;
      target.resetCount = resetCount;
      target.timeouts = timeouts;
      target.internalErrors = internalErrors;
      target.blockedCount = blockedCount;
      target.blockedTime = blockedTime;
      for (Object key : custom.keySet()) {
         CustomValue a = custom.get(key);
         // We must make sure that the key is serializable
         key = key.toString();
         CustomValue b = target.custom.get(key);
         if (a == null) {
            if (b != null) {
               b.reset();
            }
         } else if (b == null) {
            target.custom.put(key, a.clone());
         } else {
            b.reset();
            b.add(a);
         }
      }
   }

   public void addInto(StatisticsSnapshot target) {
      target.histogram.add(histogram);
      target.totalSendTime += totalSendTime;
      target.connectFailureCount += connectFailureCount;
      target.requestCount += requestCount;
      target.responseCount += responseCount;
      target.status_2xx += status_2xx;
      target.status_3xx += status_3xx;
      target.status_4xx += status_4xx;
      target.status_5xx += status_5xx;
      target.status_other += status_other;
      target.invalid += invalid;
      target.cacheHits += cacheHits;
      target.resetCount += resetCount;
      target.timeouts += timeouts;
      target.internalErrors += internalErrors;
      target.blockedCount += blockedCount;
      target.blockedTime += blockedTime;
      for (Object key : custom.keySet()) {
         CustomValue a = custom.get(key);
         // We must make sure that the key is serializable
         key = key.toString();
         CustomValue b = target.custom.get(key);
         if (a == null) {
            // noop
         } else if (b == null) {
            target.custom.put(key, a.clone());
         } else {
            b.add(a);
         }
      }
   }

   private void copySequenceId(StatisticsSnapshot target) {
      if (sequenceId >= 0) {
         if (target.sequenceId >= 0 && sequenceId != target.sequenceId) {
            throw new IllegalArgumentException("Snapshot sequence IDs don't match");
         }
         target.sequenceId = sequenceId;
      }
   }

   public void subtractFrom(StatisticsSnapshot target) {
      target.histogram.subtract(histogram);
      target.totalSendTime -= totalSendTime;
      target.connectFailureCount -= connectFailureCount;
      target.requestCount -= requestCount;
      target.responseCount -= responseCount;
      target.status_2xx -= status_2xx;
      target.status_3xx -= status_3xx;
      target.status_4xx -= status_4xx;
      target.status_5xx -= status_5xx;
      target.status_other -= status_other;
      target.invalid -= invalid;
      target.cacheHits -= cacheHits;
      target.resetCount -= resetCount;
      target.timeouts -= timeouts;
      target.internalErrors -= internalErrors;
      target.blockedCount -= blockedCount;
      target.blockedTime -= blockedTime;
      for (Object key : custom.keySet()) {
         CustomValue a = custom.get(key);
         // We must make sure that the key is serializable
         key = key.toString();
         CustomValue b = target.custom.get(key);
         if (a == null) {
            // noop
         } else if (b == null) {
            b = a.clone();
            b.reset();
            b.substract(a);
            target.custom.put(key, b);
         } else {
            b.substract(a);
         }
      }
   }

   public StatisticsSummary summary(double[] percentiles) {
      TreeMap<Double, Long> percentilesMap = DoubleStream.of(percentiles).collect(TreeMap::new,
            (map, p) -> map.put(p * 100, histogram.getValueAtPercentile(p * 100)), TreeMap::putAll);
      return new StatisticsSummary(histogram.getStartTimeStamp(), histogram.getEndTimeStamp(),
            histogram.getMinValue(), (long) histogram.getMean(), histogram.getMaxValue(),
            responseCount > 0 ? totalSendTime / responseCount : resetCount,
            percentilesMap, connectFailureCount, requestCount, responseCount,
            status_2xx, status_3xx, status_4xx, status_5xx, status_other, invalid, cacheHits, resetCount, timeouts, internalErrors, blockedCount, blockedTime);
   }

   public long errors() {
      return status_4xx + status_5xx + connectFailureCount + resetCount + timeouts + internalErrors;
   }

   @Override
   public String toString() {
      return "StatisticsSnapshot{" +
            "sequenceId=" + sequenceId +
            ", start=" + histogram.getStartTimeStamp() +
            ", end=" + histogram.getEndTimeStamp() +
            ", totalSendTime=" + totalSendTime +
            ", connectFailureCount=" + connectFailureCount +
            ", requestCount=" + requestCount +
            ", responseCount=" + responseCount +
            ", status_2xx=" + status_2xx +
            ", status_3xx=" + status_3xx +
            ", status_4xx=" + status_4xx +
            ", status_5xx=" + status_5xx +
            ", status_other=" + status_other +
            ", invalid=" + invalid +
            ", cacheHits=" + cacheHits +
            ", resetCount=" + resetCount +
            ", timeouts=" + timeouts +
            ", internalErros=" + internalErrors +
            ", blockedCount=" + blockedCount +
            ", blockedTime=" + blockedTime +
            ", custom=" + custom +
            '}';
   }
}
