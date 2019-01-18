package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.Collection;

import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableSupplier;

public class SLABuilder {
   private final SequenceBuilder parent;
   private long window = -1;
   private double errorRate = 1.01; // 101% of errors allowed
   private long meanResponseTime = Long.MAX_VALUE;
   private final Collection<SLA.PercentileLimit> limits = new ArrayList<>();
   private SLA sla;

   public SLABuilder(SequenceBuilder parent) {
      this.parent = parent;
   }

   public SLA build(SerializableSupplier<Sequence> sequence) {
      if (sla != null) {
         return sla;
      }
      return sla = new SLA(sequence, window, errorRate, meanResponseTime, limits);
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
