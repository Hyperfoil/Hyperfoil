package http2.bench.jfr;

/**
 * Signals that JFR is not available in this JVM.
 *
 * <p>It can be due to an unsupported JDK (OpenJDK, Java 7), or missing VM arguments. {@literal msg}
 * always describe why JFR is not available and what the user should do to enable it.</p>
 */
public class JfrNotAvailable extends RuntimeException {

  public JfrNotAvailable(String msg) {
    super(msg);
  }
}