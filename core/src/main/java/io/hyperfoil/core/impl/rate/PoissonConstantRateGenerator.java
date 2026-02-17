package io.hyperfoil.core.impl.rate;

import java.util.Random;

/**
 * This generator is computing the time for the next event to be scheduled in order that the total events
 * follow a homogeneous Poisson process.<br>
 * <br>
 * Given this formula to compute the next fire time based on the previous one<br>
 * <br>
 * <code>fireTime(t) = fireTime(t -1) + T</code><br>
 * <br>
 * {@code T} is a random variable that follows an exponential distribution with rate {@code fireTimesPerSec}.<br>
 * <br>
 * The formula to compute the next fire time is:<br>
 * <br>
 * <code>fireTime(t) = fireTime(t -1) + -log(rand) / fireTimesPerSec</code><br>
 * <br>
 * where the T part (exponential distribution) is computed as {@code -log(rand) / fireTimesPerSec}
 * and uses the <a href="https://en.wikipedia.org/wiki/Inverse_transform_sampling">inverse transform sampling method<a/>
 * to generate such intervals following an exponential distribution.<br>
 * <br>
 * Specifically, the exponential distribution is defined as:<br>
 * <br>
 * <code>f(x) = fireTimesPerSec * exp(-fireTimesPerSec * x)</code><br>
 * <br>
 * where {@code x} is the random variable and {@code fireTimesPerSec} is the rate of the distribution.<br>
 * <br>
 * In order to generate samples which follow it we need to compute the inverse function of the cumulative distribution function
 * (CDF).<br>
 * <br>
 * The CDF of the exponential distribution is obtained by integrating the probability density function (PDF) from 0 to {@code x}
 * and is defined as:<br>
 * <br>
 * <code>F(x) = 1 - exp(-fireTimesPerSec * x)</code><br>
 * <br>
 * We can transform this function by performing the logarithm of both sides:<br>
 * <br>
 * <code>log(1 - F(x)) = -fireTimesPerSec * x</code><br>
 * <br>
 * Resolving the previous equation for {@code x} we get:<br>
 * <br>
 * <code>x = -log(1 - F(x)) / fireTimesPerSec</code><br>
 * <br>
 * But given that {@code F(x)} is a random variable that follows a uniform distribution between 0 and 1, {@code 1 - F(x)}
 * is also a random variable that follows a uniform distribution between 0 and 1.<br>
 * <br>
 * Therefore, the formula to generate samples that follow an exponential distribution is:<br>
 * <br>
 * <code>x = -log(rand) / fireTimesPerSec</code><br>
 * <br>
 * which is the inverse function of the CDF of the exponential distribution and {@code x} is a random variable
 * that follows a uniform distribution between 0 and 1.<br>
 * <br>
 */
final class PoissonConstantRateGenerator extends SequentialRateGenerator {

   private final Random random;
   private final double fireTimesPerSec;

   PoissonConstantRateGenerator(final Random random, final double fireTimesPerSec) {
      this.random = random;
      this.fireTimesPerSec = fireTimesPerSec;
      this.fireTimeNs = nextFireTimeNs(0);
   }

   @Override
   protected double nextFireTimeNs(final double elapsedTimeNs) {
      // the Math.max(1e-20, random.nextDouble()) is to prevent the logarithm to be negative infinity, because
      // the random number can approach to 0 and the logarithm of 0 is negative infinity
      return elapsedTimeNs + 1_000_000_000.0 * -Math.log(Math.max(1e-20, random.nextDouble())) / fireTimesPerSec;
   }
}
