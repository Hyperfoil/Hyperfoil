package http2.bench;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Env {

  public static int numCore() {
    String val = System.getProperty("bench.core", "1");
    return Integer.parseInt(val);
  }

}
