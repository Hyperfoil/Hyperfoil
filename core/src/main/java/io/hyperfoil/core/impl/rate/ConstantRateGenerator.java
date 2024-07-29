package io.hyperfoil.core.impl.rate;

final class ConstantRateGenerator extends FunctionalRateGenerator {

   private final double fireTimesPerSec;

   ConstantRateGenerator(final double fireTimesPerSec) {
      this.fireTimesPerSec = fireTimesPerSec;
   }

   @Override
   protected long computeFireTimes(final long elapsedTimeMs) {
      return (long) (elapsedTimeMs * fireTimesPerSec / 1000);
   }

   @Override
   protected double computeFireTimeMs(final long targetFireTimes) {
      return 1000 * targetFireTimes / fireTimesPerSec;
   }

}
