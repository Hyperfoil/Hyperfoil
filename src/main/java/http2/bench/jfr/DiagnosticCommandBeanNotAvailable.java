package http2.bench.jfr;

/**
 * Signals that the {@link com.sun.management.DiagnosticCommandMBean} is not available. It should
 * never happen since JFR is only available on HotSpot.
 */
public class DiagnosticCommandBeanNotAvailable extends JfrNotAvailable {

  public DiagnosticCommandBeanNotAvailable() {
    super("");
  }
}