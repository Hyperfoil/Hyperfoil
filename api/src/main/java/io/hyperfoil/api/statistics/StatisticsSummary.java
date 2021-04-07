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
   public final int invalid;
   public final int resetCount;
   public final int timeouts;
   public final int internalErrors;
   public final int blockedCount;
   public final long blockedTime;
   public final SortedMap<String, StatsExtension> extensions;

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
                            @JsonProperty("invalid") int invalid,
                            @JsonProperty("resetCount") int resetCount,
                            @JsonProperty("timeouts") int timeouts,
                            @JsonProperty("internalErrors") int internalErrors,
                            @JsonProperty("blockedCount") int blockedCount,
                            @JsonProperty("blockedTime") long blockedTime,
                            @JsonProperty("custom") SortedMap<String, StatsExtension> extensions) {
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
      this.invalid = invalid;
      this.resetCount = resetCount;
      this.timeouts = timeouts;
      this.internalErrors = internalErrors;
      this.blockedCount = blockedCount;
      this.blockedTime = blockedTime;
      this.extensions = extensions;
   }

   public static void printHeader(PrintWriter writer, double[] percentiles) {
      writer.print("Requests,Responses,Mean,Min,");
      for (double p : percentiles) {
         writer.print('p');
         writer.print(p * 100);
         writer.print(',');
      }
      writer.print("Max,MeanSendTime,ConnFailure,Reset,Timeouts,Invalid,BlockedCount,BlockedTime,InternalErrors");
   }

   public void printTo(PrintWriter writer, String[] extensionHeaders) {
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
      writer.print(invalid);
      writer.print(',');
      writer.print(blockedCount);
      writer.print(',');
      writer.print(blockedTime);
      writer.print(',');
      writer.print(internalErrors);
      for (String header : extensionHeaders) {
         writer.print(',');
         int index = header.indexOf('.');
         StatsExtension value = extensions.get(header.substring(0, index));
         if (value != null) {
            writer.print(value.byHeader(header.substring(index + 1)));
         }
      }
   }
}
