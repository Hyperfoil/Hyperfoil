package io.hyperfoil.core.impl.rate;

final class ConstantRateGenerator extends FunctionalRateGenerator {

   private final double fireTimesPerSec;

   ConstantRateGenerator(final double fireTimesPerSec) {
      this.fireTimesPerSec = fireTimesPerSec;
   }

   @Override
   protected long computeFireTimes(final long elapsedTimeNs) {
      return (long) (elapsedTimeNs * fireTimesPerSec / 1_000_000_000.0);
   }

   @Override
   protected double computeFireTimeNs(final long targetFireTimes) {
      return 1_000_000_000.0 * targetFireTimes / fireTimesPerSec;
   }

}
