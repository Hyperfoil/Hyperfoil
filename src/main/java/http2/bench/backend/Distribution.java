package http2.bench.backend;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Distribution {

  private final double[] bounds;
  private final long[] percentiles;

  public Distribution(long... percentiles) {
    this.percentiles = percentiles.clone();
    this.bounds = new double[percentiles.length];
    double val = 0.9;
    for (int i = 0;i < percentiles.length;i++) {
      bounds[i] = val;
      val = 0.9 + val / 10;
    }
    bounds[bounds.length - 1] = 1;
  }

  public long next() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    double val = random.nextDouble();
    for (int i = 0;i < bounds.length;i++) {
      if (val < bounds[i]) {
        return percentiles[i];
      }
    }
    throw new UnsupportedOperationException();
  }
}
