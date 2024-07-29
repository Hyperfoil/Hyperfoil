package io.hyperfoil.core.impl.rate;

import java.util.Random;

/**
 * This generator is computing the time for the next event to be scheduled in order that the total events
 * follow a non-homogeneous Poisson process with a linearly increasing rate.<br>
 * Similarly to {@link PoissonConstantRateGenerator}, the generator is computing the time for the next event
 * to be scheduled based on the previous one, but the inverse transform sampling method to produce samples
 * depends on rate(t) instead of a constant rate i.e. fireTimesPerSec.<br>
 * Such function is defined as:<br>
 * {@code T = -log(rand) / rate(t)}<br>
 * The rate function <code>rate(t)</code> is a linear function of time, defined as:<br>
 * <code>rate(t) = initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (elapsedTimeMs / durationMs)</code><br>
 * To compute the time interval {@code T} until the next event, we need to solve the integral equation that accounts for the
 * non-homogeneous Poisson process with a time-varying rate function.<br>
 * The process can be broken down as follows:
 * <ol>
 * <li>Since {@code rate(t)} is not constant but changes linearly over time, we need to consider the integration of the rate
 * function over the interval [0, T].
 * <br>
 * Intuitively, we can look back at the previous formula {@code T = -log(rand) / rate(t)} as {@code rate(t) * T = -log(rand)}
 * where {@code rate(t) * T} are the number of
 * fire times in the period T.<br>
 * This information can be obtained as the area of the rate(t) function from 0 to T, which is the mentioned integral.<br>
 * <br>
 * The integral equation is:
 * {@code ∫[0 to T] (initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (t / durationMs)) dt = -log(rand)}
 * </li>
 * <li>The left-hand side of the equation is:
 * <br>
 * {@code (initialFireTimesPerSec * T) + ((targetFireTimesPerSec - initialFireTimesPerSec) * T^2) / (2 * durationMs)}
 * </li>
 * <li>Set this equal to {@code -log(rand)} and solve for {@code T}:
 * <br>
 * {@code initialFireTimesPerSec * T + ((targetFireTimesPerSec - initialFireTimesPerSec) * T^2) / (2 * durationMs) = -log(rand)}
 * </li>
 * <li>This is a quadratic equation of the form:
 * <br>
 * {@code aT^2 + bT + c = 0}
 * <br>
 * where:
 * <ul>
 * <li>{@code a = (targetFireTimesPerSec - initialFireTimesPerSec) / (2 * durationMs)}</li>
 * <li>{@code b = initialFireTimesPerSec}</li>
 * <li>{@code c = -log(rand)}</li>
 * </ul>
 * </li>
 * <li>Use the quadratic formula to solve for {@code T}:
 * <br>
 * {@code T = (-b ± sqrt(b^2 - 4ac)) / (2a)}
 * </li>
 * <li>Since time intervals cannot be negative, choose the positive root:
 * <br>
 * {@code T = (-initialFireTimesPerSec + sqrt(initialFireTimesPerSec^2 - 4 * (targetFireTimesPerSec - initialFireTimesPerSec) * (-log(rand) / (2 * durationMs)))) / ((targetFireTimesPerSec - initialFireTimesPerSec) / durationMs)}
 * </li>
 * <li>Simplify the expression to get the value of {@code T}.
 * <br>
 * The result is the time interval {@code T} for the next event to be scheduled. Add this interval to the current elapsed time.
 * </li>
 * </ol>
 */
final class PoissonRampRateGenerator extends SequentialRateGenerator {

   private final double initialFireTimesPerSec;
   private final Random random;
   private final long duration;
   private final double aCoef;

   PoissonRampRateGenerator(final Random random, final double initialFireTimesPerSec, final double targetFireTimesPerSec,
         final long durationMs) {
      this.initialFireTimesPerSec = initialFireTimesPerSec;
      this.duration = durationMs;
      this.aCoef = (targetFireTimesPerSec - initialFireTimesPerSec);
      this.random = random;
      fireTimeMs = nextFireTimeMs(0);
   }

   @Override
   protected double nextFireTimeMs(final double elapsedTimeMs) {
      // we're solving quadratic equation coming from t = (duration * -log(rand))/(((t + now) * (target - initial)) + initial * duration)
      final double bCoef = elapsedTimeMs * aCoef + initialFireTimesPerSec * duration;
      final double cCoef = duration * 1000 * Math.log(random.nextDouble());
      return elapsedTimeMs + (-bCoef + Math.sqrt(bCoef * bCoef - 4 * aCoef * cCoef)) / (2 * aCoef);
   }
}
