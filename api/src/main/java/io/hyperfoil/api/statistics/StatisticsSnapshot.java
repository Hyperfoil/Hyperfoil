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
   // used to track the current sample id over time
   public int sampleId = -1;
   public final Histogram histogram = new Histogram(TimeUnit.MINUTES.toNanos(1), 2);
   public int requestCount;
   public int responseCount;
   public int invalid;
   public int connectionErrors;
   public int requestTimeouts;
   public int internalErrors;
   public long blockedTime;
   public final Map<String, StatsExtension> extensions = new HashMap<>();

   public boolean isEmpty() {
      return requestCount + responseCount + invalid + connectionErrors + requestTimeouts + internalErrors == 0 &&
            extensions.values().stream().allMatch(StatsExtension::isNull);
   }

   public void reset() {
      histogram.reset();
      requestCount = 0;
      responseCount = 0;
      invalid = 0;
      connectionErrors = 0;
      requestTimeouts = 0;
      internalErrors = 0;
      blockedTime = 0;
      for (StatsExtension value : extensions.values()) {
         if (value != null) {
            value.reset();
         }
      }
   }

   public StatisticsSnapshot clone() {
      StatisticsSnapshot copy = new StatisticsSnapshot();
      copy.sampleId = sampleId;
      copy.add(this);
      return copy;
   }

   public void add(StatisticsSnapshot other) {
      histogram.add(other.histogram);
      requestCount += other.requestCount;
      responseCount += other.responseCount;
      invalid += other.invalid;
      connectionErrors += other.connectionErrors;
      requestTimeouts += other.requestTimeouts;
      internalErrors += other.internalErrors;
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
      requestCount -= other.requestCount;
      responseCount -= other.responseCount;
      invalid -= other.invalid;
      connectionErrors -= other.connectionErrors;
      requestTimeouts -= other.requestTimeouts;
      internalErrors -= other.internalErrors;
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
            histogram.getMinValue(), (long) histogram.getMean(), (long) histogram.getStdDeviation(), histogram.getMaxValue(),
            percentilesMap, requestCount, responseCount,
            invalid, connectionErrors, requestTimeouts, internalErrors, blockedTime, new TreeMap<>(extensions));
   }

   public TreeMap<Double, Long> getPercentiles(double[] percentiles) {
      return DoubleStream.of(percentiles).collect(TreeMap::new,
            (map, p) -> map.put(p, histogram.getValueAtPercentile(p)), TreeMap::putAll);
   }

   public long errors() {
      return connectionErrors + requestTimeouts + internalErrors;
   }

   @Override
   public String toString() {
      return "StatisticsSnapshot{" +
            "sequenceId=" + sampleId +
            ", start=" + histogram.getStartTimeStamp() +
            ", end=" + histogram.getEndTimeStamp() +
            ", requestCount=" + requestCount +
            ", responseCount=" + responseCount +
            ", invalid=" + invalid +
            ", connectionErrors=" + connectionErrors +
            ", requestTimeouts=" + requestTimeouts +
            ", internalErrors=" + internalErrors +
            ", blockedTime=" + blockedTime +
            ", extensions=" + extensions + '}';
   }

}
