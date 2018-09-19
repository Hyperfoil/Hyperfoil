package io.sailrocket.api.statistics;

import java.io.Serializable;
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
   }

   public StatisticsSummary summary(double[] percentiles) {
      long[] percentileValues = DoubleStream.of(percentiles).mapToLong(histogram::getValueAtPercentile).toArray();
      return new StatisticsSummary(histogram.getStartTimeStamp(), histogram.getEndTimeStamp(),
            histogram.getMinValue(), (long) histogram.getMean(), histogram.getMaxValue(),
            percentileValues, connectFailureCount, requestCount, responseCount,
            status_2xx, status_3xx, status_4xx, status_5xx, status_other, resetCount);
   }

   public long errors() {
      // TODO
      return status_4xx + status_5xx + connectFailureCount + resetCount;
   }
}
