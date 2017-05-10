package http2.bench;

import io.vertx.core.json.JsonArray;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Distribution {

  private static long[] convert(JsonArray array) {
    long[] result = new long[array.size()];
    for (int i = 0;i < result.length;i++) {
      result[i] = array.getLong(i);
    }
    return result;
  }

  private static long[] convert(List<Long> array) {
    long[] result = new long[array.size()];
    for (int i = 0;i < result.length;i++) {
      result[i] = array.get(i);
    }
    return result;
  }

  private final double[] bounds;
  private final long[] percentiles;

  public Distribution(JsonArray percentiles) {
    this(convert(percentiles));
  }

  public Distribution(List<Long> percentiles) {
    this(convert(percentiles));
  }

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
