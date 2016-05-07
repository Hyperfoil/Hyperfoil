package http2.bench.jfr;

/**
 * Signals that the recording is an unconsistent state, JMX communication failed or a JFR command
 * failed.
 */
public class JfrRecordingError extends RuntimeException {

  JfrRecordingError(String msg) {
    super(msg);
  }

  JfrRecordingError(Throwable cause) {
    super(cause);
  }
}