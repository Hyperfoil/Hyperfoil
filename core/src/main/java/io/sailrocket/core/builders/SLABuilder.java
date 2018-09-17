package io.sailrocket.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.sailrocket.api.SLA;

public class SLABuilder {
   private final SequenceBuilder parent;
   private long period = -1;
   private double errorRate = 1;
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
      return sla = new SLA(parent.build(), period, errorRate, meanResponseTime, limits);
   }

   public SequenceBuilder endSLA() {
      return parent;
   }

   /**
    * Period over which the stats should be collected, in milliseconds.
    * @param period
    * @return
    */
   public SLABuilder period(long period) {
      this.period = period;
      return this;
   }

   public SLABuilder errorRate(double errorRate) {
      this.errorRate = errorRate;
      return this;
   }

   public SLABuilder meanResponseTime(long meanResponseTime) {
      this.meanResponseTime = meanResponseTime;
      return this;
   }

   public SLABuilder addPercentileLimit(double percentile, long responseTime) {
      this.limits.add(new SLA.PercentileLimit(percentile, responseTime));
      return this;
   }
}
