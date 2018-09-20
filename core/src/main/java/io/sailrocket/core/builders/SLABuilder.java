package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.sailrocket.api.config.SLA;
import io.sailrocket.core.util.Util;

public class SLABuilder {
   private final SequenceBuilder parent;
   private long window = -1;
   private double errorRate = 1.01; // 101% of errors allowed
   private long meanResponseTime = Long.MAX_VALUE;
   private final Collection<SLA.PercentileLimit> limits = new ArrayList<>();
   private SLA sla;

   public SLABuilder(SequenceBuilder parent) {
      this.parent = parent;
      parent.sla(this);
   }

   public SLA build() {
      if (sla != null) {
         return sla;
      }
      return sla = new SLA(parent.build(), window, errorRate, meanResponseTime, limits);
   }

   public SequenceBuilder endSLA() {
      return parent;
   }

   /**
    * Period over which the stats should be collected, in milliseconds.
    * @param window
    * @return
    */
   public SLABuilder window(long window) {
      this.window = window;
      return this;
   }

   public SLABuilder window(String window) {
      return window(Util.parseToMillis(window));
   }

   public SLABuilder errorRate(double errorRate) {
      this.errorRate = errorRate;
      return this;
   }

   public SLABuilder meanResponseTime(long meanResponseTime) {
      this.meanResponseTime = meanResponseTime;
      return this;
   }

   public SLABuilder meanResponseTime(String meanResponseTime) {
      return meanResponseTime(Util.parseToNanos(meanResponseTime));
   }

   public SLABuilder addPercentileLimit(double percentile, long responseTime) {
      this.limits.add(new SLA.PercentileLimit(percentile, responseTime));
      return this;
   }
}
