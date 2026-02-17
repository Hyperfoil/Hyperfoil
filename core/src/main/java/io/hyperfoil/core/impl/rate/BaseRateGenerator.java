package io.hyperfoil.core.impl.rate;

public abstract class BaseRateGenerator implements RateGenerator {

   protected double fireTimeNs;
   protected long fireTimes;

   public BaseRateGenerator() {
      fireTimeNs = 0;
   }

   @Override
   public long fireTimes() {
      return fireTimes;
   }

   @Override
   public long lastComputedFireTimeNs() {
      return (long) Math.ceil(fireTimeNs);
   }
}
