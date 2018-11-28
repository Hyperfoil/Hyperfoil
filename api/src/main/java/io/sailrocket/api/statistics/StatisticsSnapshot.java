package io.sailrocket.api.statistics;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.DoubleStream;

import org.HdrHistogram.Histogram;

/**
 * Non-thread safe mutable set of values.
 */
public class StatisticsSnapshot implements Serializable {
   public final Histogram histogram = new Histogram(TimeUnit.MINUTES.toNanos(1), 2);
   public int connectFailureCount;
   public int requestCount;
   public int responseCount;
   public int status_2xx;
   public int status_3xx;
   public int status_4xx;
   public int status_5xx;
   public int status_other;
   public int resetCount;
   public int timeouts;
   public int blockedCount;
   public long blockedTime;
   public final Map<String, CustomValue> custom = new HashMap<>();

   public int[] statuses() {
      return new int[] { status_2xx, status_3xx, status_4xx, status_5xx, status_other };
   }

   public void reset() {
      histogram.reset();
      connectFailureCount = 0;
      requestCount = 0;
      responseCount = 0;
      status_2xx = 0;
      status_3xx = 0;
      status_4xx = 0;
      status_5xx = 0;
      status_other = 0;
      resetCount = 0;
      timeouts = 0;
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
      histogram.copyInto(target.histogram);
      target.connectFailureCount = connectFailureCount;
      target.requestCount = requestCount;
      target.responseCount = responseCount;
      target.status_2xx = status_2xx;
      target.status_3xx = status_3xx;
      target.status_4xx = status_4xx;
      target.status_5xx = status_5xx;
      target.status_other = status_other;
      target.resetCount = resetCount;
      target.timeouts = timeouts;
      target.blockedCount = blockedCount;
      target.blockedTime = blockedTime;
      for (String key : custom.keySet()) {
         CustomValue a = custom.get(key);
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
      target.connectFailureCount += connectFailureCount;
      target.requestCount += requestCount;
      target.responseCount += responseCount;
      target.status_2xx += status_2xx;
      target.status_3xx += status_3xx;
      target.status_4xx += status_4xx;
      target.status_5xx += status_5xx;
      target.status_other += status_other;
      target.resetCount += resetCount;
      target.timeouts += timeouts;
      target.blockedCount += blockedCount;
      target.blockedTime += blockedTime;
      for (String key : custom.keySet()) {
         CustomValue a = custom.get(key);
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

   public void subtractFrom(StatisticsSnapshot target) {
      target.histogram.subtract(histogram);
      target.connectFailureCount -= connectFailureCount;
      target.requestCount -= requestCount;
      target.responseCount -= responseCount;
      target.status_2xx -= status_2xx;
      target.status_3xx -= status_3xx;
      target.status_4xx -= status_4xx;
      target.status_5xx -= status_5xx;
      target.status_other -= status_other;
      target.resetCount -= resetCount;
      target.timeouts -= timeouts;
      target.blockedCount -= blockedCount;
      target.blockedTime -= blockedTime;
      for (String key : custom.keySet()) {
         CustomValue a = custom.get(key);
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
      long[] percentileValues = DoubleStream.of(percentiles).map(p -> p * 100).mapToLong(histogram::getValueAtPercentile).toArray();
      return new StatisticsSummary(histogram.getStartTimeStamp(), histogram.getEndTimeStamp(),
            histogram.getMinValue(), (long) histogram.getMean(), histogram.getMaxValue(),
            percentileValues, connectFailureCount, requestCount, responseCount,
            status_2xx, status_3xx, status_4xx, status_5xx, status_other, resetCount, timeouts, blockedCount, blockedTime);
   }

   public long errors() {
      // TODO
      return status_4xx + status_5xx + connectFailureCount + resetCount + timeouts;
   }
}
