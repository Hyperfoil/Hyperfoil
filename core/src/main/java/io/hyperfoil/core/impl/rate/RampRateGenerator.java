package io.hyperfoil.core.impl.rate;

/**
 * This is a generator that generates fire times at a ramping rate.
 * The rate of events starts at an initial rate and increases linearly to a target rate over a specified duration.
 * <p>
 * The rate function is a linear function of time, defined as:
 *
 * <pre>{@code
 * rate(t) = initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (elapsedTimeNs / durationNs)
 * }</pre>
 *
 * To find the time at which the Nth event occurs, we integrate the rate function and solve for t:
 *
 * <pre>{@code
 * N = (initialFireTimesPerSec * t) + ((targetFireTimesPerSec - initialFireTimesPerSec) * t ^ 2) / (2 * durationNs)
 * }</pre>
 *
 * This is a quadratic equation of the form aT^2 + bT + c = 0 where:
 * <ul>
 * <li>{@code a = (targetFireTimesPerSec - initialFireTimesPerSec) / (2 * durationNs)}</li>
 * <li>{@code b = initialFireTimesPerSec}</li>
 * <li>{@code c = -N}</li>
 * </ul>
 * Solving this quadratic equation for {@code t} provides the time at which the Nth event should be scheduled.
 */
final class RampRateGenerator implements FireTimeSequence {

   private final double bCoef;
   private final double progress;
   private long index;

   RampRateGenerator(final double initialFireTimesPerSec, final double targetFireTimesPerSec, final long durationNs) {
      bCoef = initialFireTimesPerSec / 1_000_000_000.0;
      progress = (targetFireTimesPerSec - initialFireTimesPerSec) / ((double) durationNs * 1_000_000_000.0);
   }

   @Override
   public long nextFireTimeNs() {
      long targetFireTimes = ++index;
      return (long) Math.ceil((-bCoef + Math.sqrt(bCoef * bCoef + 2 * progress * targetFireTimes)) / progress);
   }
}
