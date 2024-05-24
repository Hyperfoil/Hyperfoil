package io.hyperfoil.core.impl.rate;


/**
 * This is a generator that generates events (fire times) at a ramping rate.
 * The rate of events starts at an initial rate and increases linearly to a target rate over a specified duration.
 * <p>
 * The rate function is a linear function of time, defined as:
 * <pre>{@code
 * rate(t) = initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (elapsedTimeMs / durationMs)
 * }</pre>
 * To find the required number of events at time t, we need to integrate the rate function from 0 to t:
 * <pre>{@code
 * requiredFireTimes(t) = ∫[0 to t] (initialFireTimesPerSec + (targetFireTimesPerSec - initialFireTimesPerSec) * (t / durationMs)) dt
 * }</pre>
 * Which simplifies to:
 * <pre>{@code
 * requiredFireTimes(t) = (initialFireTimesPerSec * t) + ((targetFireTimesPerSec - initialFireTimesPerSec) * t^2) / (2 * durationMs)
 * }</pre>
 * Given the elapsed time {@code t} we can use the above formula to compute the required number of events.<br>
 * To obtain the next fire time, we need to solve the integral equation for the required number of events at time t.<br>
 * Assuming the required number of events at time t is known (using the previous formula and adding another one),
 * we can resolve the previous equation for t rearranging it into a quadratic equation of the form aT^2 + bT + c = 0.<br>
 * <p>
 * Let:
 * <ul>
 *     <li>{@code a = (targetFireTimesPerSec - initialFireTimesPerSec) / (2 * durationMs)}</li>
 *     <li>{@code b = initialFireTimesPerSec}</li>
 *     <li>{@code c = -requiredFireTimes(t)}</li>
 * </ul>
 * Solving this quadratic equation for {@code t} provides the time at which the next event should be scheduled.
 */
final class RampRateGenerator extends FunctionalRateGenerator {

    private final double bCoef;
    private final double progress;

    RampRateGenerator(final double initialFireTimesPerSec, final double targetFireTimesPerSec, final long durationMs) {
        bCoef = initialFireTimesPerSec / 1000;
        progress = (targetFireTimesPerSec - initialFireTimesPerSec) / (durationMs * 1000);
    }

    @Override
    protected long computeFireTimes(final long elapsedTimeMs) {
        return (long) ((progress * elapsedTimeMs / 2 + bCoef) * elapsedTimeMs);
    }

    @Override
    protected double computeFireTimeMs(final long targetFireTimes) {
        // root of quadratic equation
        return Math.ceil((-bCoef + Math.sqrt(bCoef * bCoef + 2 * progress * targetFireTimes)) / progress);
    }
}
