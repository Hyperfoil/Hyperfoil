package io.hyperfoil.api.statistics;

import java.io.PrintWriter;
import java.util.SortedMap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class StatisticsSummary {
   public final long startTime;
   public final long endTime;
   public final long minResponseTime;
   public final long meanResponseTime;
   public final long maxResponseTime;
   public final long meanSendTime;
   public final SortedMap<Double, Long> percentileResponseTime; // the percentiles depend on configuration
   public final int connectFailureCount;
   public final int requestCount;
   public final int responseCount;
   public final int status_2xx;
   public final int status_3xx;
   public final int status_4xx;
   public final int status_5xx;
   public final int status_other;
   public final int invalid;
   public final int cacheHits;
   public final int resetCount;
   public final int timeouts;
   public final int internalErrors;
   public final int blockedCount;
   public final long blockedTime;

   @JsonCreator
   public StatisticsSummary(@JsonProperty("startTime") long startTime,
                            @JsonProperty("endTime") long endTime,
                            @JsonProperty("minResponseTime") long minResponseTime,
                            @JsonProperty("meanResponseTime") long meanResponseTime,
                            @JsonProperty("maxResponseTime") long maxResponseTime,
                            @JsonProperty("meanSendTime") long meanSendTime,
                            @JsonProperty("percentileResponseTime") SortedMap<Double, Long> percentileResponseTime,
                            @JsonProperty("connectFailureCount") int connectFailureCount,
                            @JsonProperty("requestCount") int requestCount,
                            @JsonProperty("responseCount") int responseCount,
                            @JsonProperty("status_2xx") int status_2xx,
                            @JsonProperty("status_3xx") int status_3xx,
                            @JsonProperty("status_4xx") int status_4xx,
                            @JsonProperty("status_5xx") int status_5xx,
                            @JsonProperty("status_other") int status_other,
                            @JsonProperty("invalid") int invalid,
                            @JsonProperty("cacheHits") int cacheHits,
                            @JsonProperty("resetCount") int resetCount,
                            @JsonProperty("timeouts") int timeouts,
                            @JsonProperty("internalErrors") int internalErrors,
                            @JsonProperty("blockedCount") int blockedCount,
                            @JsonProperty("blockedTime") long blockedTime) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.minResponseTime = minResponseTime;
      this.meanResponseTime = meanResponseTime;
      this.maxResponseTime = maxResponseTime;
      this.meanSendTime = meanSendTime;
      this.percentileResponseTime = percentileResponseTime;
      this.connectFailureCount = connectFailureCount;
      this.requestCount = requestCount;
      this.responseCount = responseCount;
      this.status_2xx = status_2xx;
      this.status_3xx = status_3xx;
      this.status_4xx = status_4xx;
      this.status_5xx = status_5xx;
      this.status_other = status_other;
      this.invalid = invalid;
      this.cacheHits = cacheHits;
      this.resetCount = resetCount;
      this.timeouts = timeouts;
      this.internalErrors = internalErrors;
      this.blockedCount = blockedCount;
      this.blockedTime = blockedTime;
   }

   public static void printHeader(PrintWriter writer, double[] percentiles) {
      writer.print("Requests,Responses,Mean,Min,");
      for (double p : percentiles) {
         writer.print('p');
         writer.print(p * 100);
         writer.print(',');
      }
      writer.print("Max,MeanSendTime,ConnFailure,Reset,Timeouts,2xx,3xx,4xx,5xx,Other,Invalid,CacheHits,BlockedCount,BlockedTime,InternalErrors");
   }

   public void printTo(PrintWriter writer) {
      writer.print(requestCount);
      writer.print(',');
      writer.print(responseCount);
      writer.print(',');
      writer.print(meanResponseTime);
      writer.print(',');
      writer.print(minResponseTime);
      writer.print(',');
      for (long prt : percentileResponseTime.values()) {
         writer.print(prt);
         writer.print(',');
      }
      writer.print(maxResponseTime);
      writer.print(',');
      writer.print(meanSendTime);
      writer.print(',');
      writer.print(connectFailureCount);
      writer.print(',');
      writer.print(resetCount);
      writer.print(',');
      writer.print(timeouts);
      writer.print(',');
      writer.print(status_2xx);
      writer.print(',');
      writer.print(status_3xx);
      writer.print(',');
      writer.print(status_4xx);
      writer.print(',');
      writer.print(status_5xx);
      writer.print(',');
      writer.print(status_other);
      writer.print(',');
      writer.print(invalid);
      writer.print(',');
      writer.print(cacheHits);
      writer.print(',');
      writer.print(blockedCount);
      writer.print(',');
      writer.print(blockedTime);
      writer.print(',');
      writer.print(internalErrors);
   }
}
