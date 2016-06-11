package http2.bench;

import java.math.BigInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Utils {

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
}
