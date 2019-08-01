package io.hyperfoil.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.util.Util;

public class SLABuilder<P> implements Rewritable<SLABuilder<P>> {
   private final P parent;
   private long window = -1;
   private double errorRatio = 1.01; // 101% of errors allowed
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
      return sla = new SLA(window, errorRatio, meanResponseTime, blockedRatio, limits);
   }

   public P endSLA() {
      return parent;
   }

   /**
    * Period over which the stats should be collected, in milliseconds.
    * @param window
    * @return
    */
   public SLABuilder<P> window(long window, TimeUnit timeUnit) {
      this.window = timeUnit.toMillis(window);
      return this;
   }

   public SLABuilder<P> window(String window) {
      return window(Util.parseToMillis(window), TimeUnit.MILLISECONDS);
   }

   public SLABuilder<P> errorRatio(double errorRatio) {
      this.errorRatio = errorRatio;
      return this;
   }

   public SLABuilder<P> meanResponseTime(long meanResponseTime, TimeUnit timeUnit) {
      this.meanResponseTime = timeUnit.toNanos(meanResponseTime);
      return this;
   }

   public SLABuilder<P> meanResponseTime(String meanResponseTime) {
      return meanResponseTime(Util.parseToNanos(meanResponseTime), TimeUnit.NANOSECONDS);
   }

   public SLABuilder<P> blockedRatio(double blockedRatio) {
      this.blockedRatio = blockedRatio;
      return this;
   }

   public SLABuilder<P> addPercentileLimit(double percentile, long responseTime) {
      this.limits.add(new SLA.PercentileLimit(percentile, responseTime));
      return this;
   }

   public LimitsBuilder limits() {
      return new LimitsBuilder();
   }

   @Override
   public void readFrom(SLABuilder<P> other) {
      window = other.window;
      errorRatio = other.errorRatio;
      meanResponseTime = other.meanResponseTime;
      blockedRatio = other.blockedRatio;
      limits.clear();
      limits.addAll(limits);
   }

   private class LimitsBuilder extends PairBuilder.OfString {
      @Override
      public void accept(String percentileStr, String responseTime) {
         double percentile = Double.parseDouble(percentileStr);
         if (percentile < 0 || percentile > 1) {
            throw new BenchmarkDefinitionException("Percentile must be between 0.0 and 1.0");
         }
         addPercentileLimit(percentile, Util.parseToNanos(responseTime));
      }
   }

   public static class ListBuilder<P> implements MappingListBuilder<SLABuilder<ListBuilder<P>>>, Rewritable<ListBuilder<P>> {
      private final P parent;
      private final ArrayList<SLABuilder<ListBuilder<P>>> sla = new ArrayList<>();

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
