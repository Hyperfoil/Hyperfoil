package io.sailrocket.api;

import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;

/**
 * Non-thread safe set of values.
 */
public class StatisticsSnapshot {
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
}
