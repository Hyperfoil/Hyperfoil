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
   public int invalid;
   public int resetCount;
   public int timeouts;
   public int internalErrors;
   public int blockedCount;
   public long blockedTime;
   public final Map<String, StatsExtension> extensions = new HashMap<>();

   public boolean isEmpty() {
      return connectFailureCount + requestCount + responseCount +
            invalid + resetCount + timeouts + internalErrors + blockedCount == 0 &&
            extensions.values().stream().allMatch(StatsExtension::isNull);
   }

   public void reset() {
      histogram.reset();
      totalSendTime = 0;
      connectFailureCount = 0;
      requestCount = 0;
      responseCount = 0;
      invalid = 0;
      resetCount = 0;
      timeouts = 0;
      internalErrors = 0;
      blockedCount = 0;
      blockedTime = 0;
      for (StatsExtension value : extensions.values()) {
         if (value != null) {
            value.reset();
         }
      }
   }

   public StatisticsSnapshot clone() {
      StatisticsSnapshot copy = new StatisticsSnapshot();
      copy.sequenceId = sequenceId;
      copy.add(this);
      return copy;
   }

   public void add(StatisticsSnapshot other) {
      histogram.add(other.histogram);
      totalSendTime += other.totalSendTime;
      connectFailureCount += other.connectFailureCount;
      requestCount += other.requestCount;
      responseCount += other.responseCount;
      invalid += other.invalid;
      resetCount += other.resetCount;
      timeouts += other.timeouts;
      internalErrors += other.internalErrors;
      blockedCount += other.blockedCount;
      blockedTime += other.blockedTime;
      for (String key : other.extensions.keySet()) {
         StatsExtension their = other.extensions.get(key);
         StatsExtension my = extensions.get(key);
         if (their == null) {
            // noop
         } else if (my == null) {
            extensions.put(key, their.clone());
         } else {
            my.add(their);
         }
      }
   }

   public void subtract(StatisticsSnapshot other) {
      histogram.subtract(other.histogram);
      totalSendTime -= other.totalSendTime;
      connectFailureCount -= other.connectFailureCount;
      requestCount -= other.requestCount;
      responseCount -= other.responseCount;
      invalid -= other.invalid;
      resetCount -= other.resetCount;
      timeouts -= other.timeouts;
      internalErrors -= other.internalErrors;
      blockedCount -= other.blockedCount;
      blockedTime -= other.blockedTime;
      for (String key : other.extensions.keySet()) {
         StatsExtension their = other.extensions.get(key);
         StatsExtension my = extensions.get(key);
         if (their == null) {
            // noop
         } else if (my == null) {
            my = their.clone();
            my.reset();
            my.subtract(their);
            extensions.put(key, my);
         } else {
            my.subtract(their);
         }
      }
   }

   public StatisticsSummary summary(double[] percentiles) {
      TreeMap<Double, Long> percentilesMap = getPercentiles(percentiles);
      return new StatisticsSummary(histogram.getStartTimeStamp(), histogram.getEndTimeStamp(),
            histogram.getMinValue(), (long) histogram.getMean(), histogram.getMaxValue(),
            responseCount > 0 ? totalSendTime / responseCount : resetCount,
            percentilesMap, connectFailureCount, requestCount, responseCount,
            invalid, resetCount, timeouts, internalErrors, blockedCount, blockedTime, new TreeMap<>(extensions));
   }

   public TreeMap<Double, Long> getPercentiles(double[] percentiles) {
      return DoubleStream.of(percentiles).collect(TreeMap::new,
            (map, p) -> map.put(p * 100, histogram.getValueAtPercentile(p * 100)), TreeMap::putAll);
   }

   public long errors() {
      return connectFailureCount + resetCount + timeouts + internalErrors;
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
            ", invalid=" + invalid +
            ", resetCount=" + resetCount +
            ", timeouts=" + timeouts +
            ", internalErrors=" + internalErrors +
            ", blockedCount=" + blockedCount +
            ", blockedTime=" + blockedTime +
            ", extensions=" + extensions + '}';
   }

}
