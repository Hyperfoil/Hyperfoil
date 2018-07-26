package http2.bench;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Utils {
  public static long res = 0; // value sink
  public static long ONE_MILLI_IN_NANO = 1000000;
  public static long ONE_MICRO_IN_NANO = 1000;

  public static BigInteger parseSize(String s) {
    long scale = 1;
    if (s.length() > 2) {
      int end = s.length() - 2;
      String suffix = s.substring(end);
      switch (suffix) {
        case "kb":
          scale = 1024;
          s = s.substring(0, end);
          break;
        case "mb":
          scale = 1024 * 1024;
          s = s.substring(0, end);
          break;
        case "gb":
          scale = 1024 * 1024 * 1024;
          s = s.substring(0, end);
          break;
      }
    }
    return new BigInteger(s).multiply(BigInteger.valueOf(scale));
  }

  /* the purpose of this method is to consume pure CPU without
   * additional resources (memory, io).
   * We may need to simulate milliseconds of cpu usage so
   * base calculation is somewhat complex to avoid too many iterations
   */
  public static void blackholeCpu(long iterations) {
    long result = 0;
    for (int i=0; i < iterations; i++) {
      int next = (ThreadLocalRandom.current().nextInt() % 1019) / 17;
      result = result ^ (Math.round(Math.pow(next,3)) % 251);
    }
    res += result;
  }

  /* Estimates the number of iterations of blackholeCpu needed to
   * spend one milliseconds of CPU time
   */
  public static long calibrateBlackhole() {
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    // Make the blackholeCpu method hot, to force C2 optimization
    for (int i=0; i < 50000; i++) {
      Utils.blackholeCpu(100);
    }
    // find the number of iterations needed to spend more than 1 milli
    // of cpu time
    final long[] iters = {1000,5000,10000,20000,50000,100000};
    long timing = 0;
    int i=-1;
    while (timing < ONE_MILLI_IN_NANO && ++i < iters.length) {
      long start_cpu = threadBean.getCurrentThreadCpuTime();
      Utils.blackholeCpu(iters[i]);
      timing=threadBean.getCurrentThreadCpuTime()-start_cpu;
    }
    // estimate the number of iterations for 1 milli
    return Math.round(Math.ceil((ONE_MILLI_IN_NANO*1.0/timing)*iters[i]));
  }

}
