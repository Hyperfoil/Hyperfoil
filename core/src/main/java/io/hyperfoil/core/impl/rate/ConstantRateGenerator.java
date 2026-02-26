package io.hyperfoil.core.impl.rate;

final class ConstantRateGenerator implements FireTimeSequence {

   private final double fireTimesPerSec;
   private long index;

   ConstantRateGenerator(final double fireTimesPerSec) {
      this.fireTimesPerSec = fireTimesPerSec;
   }

   @Override
   public long nextFireTimeNs() {
      return (long) Math.ceil(1_000_000_000.0 * ++index / fireTimesPerSec);
   }
}
