package io.hyperfoil.core.impl.rate;

public abstract class BaseRateGenerator implements RateGenerator {

   protected double fireTimeMs;
   protected long fireTimes;

   public BaseRateGenerator() {
      fireTimeMs = 0;
   }

   @Override
   public long fireTimes() {
      return fireTimes;
   }

   @Override
   public long lastComputedFireTimeMs() {
      return (long) Math.ceil(fireTimeMs);
   }
}
