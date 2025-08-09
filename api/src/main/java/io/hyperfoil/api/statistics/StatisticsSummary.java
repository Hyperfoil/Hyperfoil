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
   public final long stdDevResponseTime;
   public final long maxResponseTime;
   public final SortedMap<Double, Long> percentileResponseTime; // the percentiles depend on configuration
   public final int requestCount;
   public final int responseCount;
   public final int invalid;
   public final int connectionErrors;
   public final int requestTimeouts;
   public final int internalErrors;
   public final long blockedTime;
   public final SortedMap<String, StatsExtension> extensions;

   @JsonCreator
   public StatisticsSummary(@JsonProperty("startTime") long startTime,
         @JsonProperty("endTime") long endTime,
         @JsonProperty("minResponseTime") long minResponseTime,
         @JsonProperty("meanResponseTime") long meanResponseTime,
         @JsonProperty("stdDevResponseTime") long stdDevResponseTime,
         @JsonProperty("maxResponseTime") long maxResponseTime,
         @JsonProperty("percentileResponseTime") SortedMap<Double, Long> percentileResponseTime,
         @JsonProperty("requestCount") int requestCount,
         @JsonProperty("responseCount") int responseCount,
         @JsonProperty("invalid") int invalid,
         @JsonProperty("connectionErrors") int connectionErrors,
         @JsonProperty("requestTimeouts") int requestTimeouts,
         @JsonProperty("internalErrors") int internalErrors,
         @JsonProperty("blockedTime") long blockedTime,
         @JsonProperty("extensions") SortedMap<String, StatsExtension> extensions) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.minResponseTime = minResponseTime;
      this.meanResponseTime = meanResponseTime;
      this.stdDevResponseTime = stdDevResponseTime;
      this.maxResponseTime = maxResponseTime;
      this.percentileResponseTime = percentileResponseTime;
      this.requestCount = requestCount;
      this.responseCount = responseCount;
      this.invalid = invalid;
      this.connectionErrors = connectionErrors;
      this.requestTimeouts = requestTimeouts;
      this.internalErrors = internalErrors;
      this.blockedTime = blockedTime;
      this.extensions = extensions;
   }

   public static void printHeader(PrintWriter writer, double[] percentiles) {
      writer.print("Requests,Responses,Mean,StdDev,Min,");
      for (double p : percentiles) {
         writer.print('p');
         writer.print(p);
         writer.print(',');
      }
      writer.print("Max,ConnectionErrors,RequestTimeouts,InternalErrors,Invalid,BlockedTime");
   }

   public void printTo(PrintWriter writer, String[] extensionHeaders) {
      writer.print(requestCount);
      writer.print(',');
      writer.print(responseCount);
      writer.print(',');
      writer.print(meanResponseTime);
      writer.print(',');
      writer.print(stdDevResponseTime);
      writer.print(',');
      writer.print(minResponseTime);
      writer.print(',');
      for (long prt : percentileResponseTime.values()) {
         writer.print(prt);
         writer.print(',');
      }
      writer.print(maxResponseTime);
      writer.print(',');
      writer.print(connectionErrors);
      writer.print(',');
      writer.print(requestTimeouts);
      writer.print(',');
      writer.print(internalErrors);
      writer.print(',');
      writer.print(invalid);
      writer.print(',');
      writer.print(blockedTime);
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
