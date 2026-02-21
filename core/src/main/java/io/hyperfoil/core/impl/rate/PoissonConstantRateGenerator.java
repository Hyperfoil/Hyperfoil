package io.hyperfoil.core.impl.rate;

import java.util.Random;

/**
 * This generator computes fire times following a homogeneous Poisson process.
 * <p>
 * Inter-arrival times follow an exponential distribution with rate {@code fireTimesPerSec},
 * generated using the <a href="https://en.wikipedia.org/wiki/Inverse_transform_sampling">inverse transform sampling method</a>.
 * <p>
 * The formula for each inter-arrival interval is:
 *
 * <pre>{@code
 * T = -log(rand) / fireTimesPerSec
 * }</pre>
 *
 * where {@code rand} is uniformly distributed on (0, 1].
 */
final class PoissonConstantRateGenerator implements FireTimeSequence {

   private final Random random;
   private final double fireTimesPerSec;
   private double currentNs;

   PoissonConstantRateGenerator(final Random random, final double fireTimesPerSec) {
      this.random = random;
      this.fireTimesPerSec = fireTimesPerSec;
   }

   @Override
   public long nextFireTimeNs() {
      currentNs += 1_000_000_000.0 * -Math.log(Math.max(1e-20, random.nextDouble())) / fireTimesPerSec;
      return (long) Math.ceil(currentNs);
   }
}
