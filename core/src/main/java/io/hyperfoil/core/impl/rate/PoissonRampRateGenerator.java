package io.hyperfoil.core.impl.rate;

import java.util.Random;

/**
 * This generator computes fire times following a non-homogeneous Poisson process
 * with a linearly increasing rate.
 * <p>
 * The rate function is:
 *
 * <pre>{@code
 * rate(t) = initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (t / durationNs)
 * }</pre>
 * <p>
 * To compute the next inter-arrival interval T from current time t, we solve:
 *
 * <pre>{@code ∫[t to t+T] rate(u) du = -log(rand)}</pre>
 *
 * which yields a quadratic equation in T, solved via the quadratic formula.
 */
final class PoissonRampRateGenerator implements FireTimeSequence {

   private final double initialFireTimesPerSec;
   private final Random random;
   private final long duration;
   private final double aCoef;
   private double currentNs;

   PoissonRampRateGenerator(final Random random, final double initialFireTimesPerSec, final double targetFireTimesPerSec,
         final long durationNs) {
      this.initialFireTimesPerSec = initialFireTimesPerSec;
      this.duration = durationNs;
      this.aCoef = (targetFireTimesPerSec - initialFireTimesPerSec);
      this.random = random;
   }

   @Override
   public long nextFireTimeNs() {
      final double bCoef = currentNs * aCoef + initialFireTimesPerSec * duration;
      final double cCoef = (double) duration * 1_000_000_000.0 * Math.log(random.nextDouble());
      currentNs += (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
      return (long) Math.ceil(currentNs);
   }
}
