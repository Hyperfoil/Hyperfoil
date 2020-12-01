package io.hyperfoil.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.util.Util;

/**
 * Defines a Service Level Agreement (SLA) - conditions that must hold for benchmark to be deemed successful.
 */
public class SLABuilder<P> implements Rewritable<SLABuilder<P>> {
   private final P parent;
   private long window = -1;
   private double errorRatio = 1.01; // 101% of errors allowed
   private double invalidRatio = 1.01;
   private long meanResponseTime = Long.MAX_VALUE;
   private final Collection<SLA.PercentileLimit> limits = new ArrayList<>();
   private double blockedRatio = 0; // do not allow blocking
   private SLA sla;

   public SLABuilder(P parent) {
      this.parent = parent;
   }

   public void prepareBuild() {
   }

   public SLA build() {
      if (sla != null) {
         return sla;
      }
      return sla = new SLA(window, errorRatio, invalidRatio, meanResponseTime, blockedRatio, limits);
   }

   public P endSLA() {
      return parent;
   }

   public SLABuilder<P> window(long window, TimeUnit timeUnit) {
      this.window = timeUnit.toMillis(window);
      return this;
   }

   /**
    * Period over which the stats should be collected. By default the SLA applies to stats from whole phase.
    *
    * @param window Window size with suffix ('s', 'm' or 'h') or just in milliseconds.
    * @return Self.
    */
   public SLABuilder<P> window(String window) {
      return window(Util.parseToMillis(window), TimeUnit.MILLISECONDS);
   }

   /**
    * Maximum allowed ratio of errors: connection failures or resets, timeouts and internal errors. Valid values are 0.0 - 1.0 (inclusive).
    * Note: 4xx and 5xx statuses are NOT considered errors for this SLA parameter. Use <code>invalidRatio</code> for that.
    *
    * @param errorRatio Ratio.
    * @return Self.
    */
   public SLABuilder<P> errorRatio(double errorRatio) {
      this.errorRatio = errorRatio;
      return this;
   }

   /**
    * Maximum allowed ratio of responses marked as invalid. Valid values are 0.0 - 1.0 (inclusive).
    * Note: With default settings 4xx and 5xx statuses are considered invalid. Check out
    * <code>ergonomics.autoRangeCheck</code> or <code>httpRequest.handler.autoRangeCheck</code> to change this.
    *
    * @param invalidRatio Ratio.
    * @return Self.
    */
   public SLABuilder<P> invalidRatio(double invalidRatio) {
      this.invalidRatio = invalidRatio;
      return this;
   }


   public SLABuilder<P> meanResponseTime(long meanResponseTime, TimeUnit timeUnit) {
      this.meanResponseTime = timeUnit.toNanos(meanResponseTime);
      return this;
   }

   /**
    * Maximum allowed mean (average) response time. Use suffix `ns`, `us`, `ms` or `s` to specify units.
    *
    * @param meanResponseTime Mean response time.
    * @return Self.
    */
   public SLABuilder<P> meanResponseTime(String meanResponseTime) {
      return meanResponseTime(Util.parseToNanos(meanResponseTime), TimeUnit.NANOSECONDS);
   }

   /**
    * Maximum allowed ratio of time spent waiting for usable connection to sum of response latencies and blocked time.
    * Default is 0 - client must not be blocked. Set to 1 if the client can block without limits.
    *
    * @param blockedRatio Maximum ratio.
    * @return Self.
    */
   public SLABuilder<P> blockedRatio(double blockedRatio) {
      this.blockedRatio = blockedRatio;
      return this;
   }

   /**
    * Percentile limits.
    *
    * @return Builder.
    */
   public LimitsBuilder limits() {
      return new LimitsBuilder();
   }

   @Override
   public void readFrom(SLABuilder<P> other) {
      window = other.window;
      errorRatio = other.errorRatio;
      invalidRatio = other.invalidRatio;
      meanResponseTime = other.meanResponseTime;
      blockedRatio = other.blockedRatio;
      limits.clear();
      limits.addAll(limits);
   }

   /**
    * Percentile limits.
    */
   public class LimitsBuilder extends PairBuilder.OfString {
      /**
       * Use percentile (value between 0.0 and 1.0) as key and response time with unit (e.g. `ms`) in suffix as value.
       *
       * @param percentileStr Percentile (value between 0.0 and 1.0).
       * @param responseTime  Response time threshold.
       */
      @Override
      public void accept(String percentileStr, String responseTime) {
         double percentile = Double.parseDouble(percentileStr);
         if (percentile < 0 || percentile > 1) {
            throw new BenchmarkDefinitionException("Percentile must be between 0.0 and 1.0");
         }
         limits.add(new SLA.PercentileLimit(percentile, Util.parseToNanos(responseTime)));
      }

      public LimitsBuilder add(double percentile, long responseTime) {
         limits.add(new SLA.PercentileLimit(percentile, responseTime));
         return this;
      }

      public SLABuilder<P> end() {
         return SLABuilder.this;
      }
   }

   /**
    * Defines a list of Service Level Agreements (SLAs) - conditions that must hold for benchmark to be deemed successful.
    */
   public static class ListBuilder<P> implements MappingListBuilder<SLABuilder<ListBuilder<P>>>, Rewritable<ListBuilder<P>> {
      private final P parent;
      private final ArrayList<SLABuilder<ListBuilder<P>>> sla = new ArrayList<>();

      // used only for copy()
      public ListBuilder() {
         this(null);
      }

      public ListBuilder(P parent) {
         this.parent = parent;
      }

      @Override
      public SLABuilder<ListBuilder<P>> addItem() {
         SLABuilder<ListBuilder<P>> sb = new SLABuilder<>(this);
         sla.add(sb);
         return sb;
      }

      public P endList() {
         return parent;
      }

      public SLA[] build() {
         return sla.stream().map(SLABuilder::build).toArray(SLA[]::new);
      }

      @Override
      public void readFrom(ListBuilder<P> other) {
         sla.clear();
         for (SLABuilder<ListBuilder<P>> builder : other.sla) {
            addItem().readFrom(builder);
         }
      }
   }
}
