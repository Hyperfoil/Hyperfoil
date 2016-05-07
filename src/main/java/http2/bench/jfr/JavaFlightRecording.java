package http2.bench.jfr;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

public class JavaFlightRecording {

  private final Conf conf;
  private final DiagnosticCommand diagnosticCommand;
  private boolean started;

  JavaFlightRecording(Conf conf) throws JfrNotAvailable {
    this.conf = conf;
    this.diagnosticCommand = new DiagnosticCommand();
    this.started = false;

    enableJfr();
  }

  /**
   * Returns a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Checks if JFR is enabled and try to enable it if not.
   *
   * <p>Dynamic activation is only available since 1.8u40</p>
   *
   * @throws JfrNotAvailable If JFR has not been explicitly enabled and cannot be enabled
   *                         dynamically.
   */
  private void enableJfr() throws JfrNotAvailable {
    assertIsOracleJre();

    String runtimeVersion = System.getProperty("java.runtime.version");
    if (runtimeVersion.matches("1.[1-7].*")) {
      throw new JfrNotAvailable("Only JDK 1.8 or later is supported");
    } else if (runtimeVersion.matches("1.8.0_[0-4]\\d-.*")) {
      assertJfrEnabled();
    } else {
      diagnosticCommand.unlockCommercialFeatures();
    }
  }

  private void assertIsOracleJre() throws JfrNotAvailable {
    String vmName = System.getProperty("java.vm.name");
    if (vmName.contains("OpenJDK")) {
      throw new JfrNotAvailable(
          "Flight recoder not available in OpenJDK. Please use an Oracle JRE");
    }
  }

  private void assertJfrEnabled() throws JfrNotAvailable {
    RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    List<String> args = runtimeMxBean.getInputArguments();
    if (!args.contains("-XX:+UnlockCommercialFeatures") || !args.contains("-XX:+FlightRecorder")) {
      throw new JfrNotAvailable("JFR can be dynamically enabled only since 1.8u40. " +
          "Please explicitly enable JFR by adding " +
          "-XX:+UnlockCommercialFeatures -XX:+FlightRecorder to the JVM arguments");
    }
  }

  /**
   * Starts recording.
   *
   * @throws IllegalStateException If this profiling session is already started
   * @throws JfrRecordingError     If JFR recording cannot be started (should never happen)
   */
  public void start() {
    if (started) {
      throw new IllegalStateException("This recording has already been started");
    }
    started = true;

    diagnosticCommand.jfrStart(
        "name=" + conf.getName(),
        "settings=" + conf.getSettings());
  }

  /**
   * Stops recording and dumps it to disk.
   *
   * @throws IllegalStateException If this profiling session has not been started or has already
   *                               been stopped.
   * @throws JfrRecordingError     If JFR recording cannot be stopped or dumped on disk
   */
  public void stop() {
    if (!started) {
      throw new IllegalStateException();
    }
    started = false;

    String output = diagnosticCommand.jfrStop(
        "name=" + conf.getName(),
        "filename=" + conf.getOutputPath(),
        "compress=" + conf.enableCompression());

    // Poor and flaky error handling here. The output of jfrStop is not documented
    if (!output.startsWith("Stopped recording")) {
      throw new JfrRecordingError(output);
    }
  }

  /**
   * Returns the configuration of this recording
   */
  public Conf getConf() {
    return conf;
  }

  public static class Conf {

    private final Path outputPath;
    private final String name;
    private final String settings;
    private final boolean enableCompression;

    Conf(Path outputPath, String name, String settings, boolean enableCompression) {
      this.outputPath = outputPath;
      this.name = name;
      this.settings = settings;
      this.enableCompression = enableCompression;
    }

    public Path getOutputPath() {
      return outputPath;
    }

    public String getName() {
      return name;
    }

    public String getSettings() {
      return settings;
    }

    public boolean enableCompression() {
      return enableCompression;
    }
  }

  public static class Builder {

    Path outputPath;
    String settings;
    String name;
    boolean enableCompression;

    Builder() {
      settings = "profile.jfc";
      name = getRandomName();
      enableCompression = false;
    }

    private static String getRandomName() {
      return new BigInteger(32, new Random()).toString();
    }

    /**
     * Sets where the final output file will be stored.
     *
     * <p>If not specified, the recording will be dumped in the current working directory with
     * {@literal ${name}.jfr} as filename.</p>
     *
     * <p>It is recommended to use {@literal .jfr} as filename extension</p>
     */
    public Builder withOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    /**
     * Sets where the final output file will be stored.
     *
     * <p>If not specified, the recording will be dumped in the current working directory with
     * {@literal ${name}.jfr} as filename.</p>
     *
     * <p>It is recommended to use {@literal .jfr} as filename extension</p>
     */
    public Builder withOutputPath(String outputPath) {
      return withOutputPath(Paths.get(outputPath));
    }

    /**
     * Sets the JFR settings file to use.
     *
     * <p>If not specified, {@literal profile.jfc} is used.</p>
     *
     * <p>The settings parameter takes either the path to, or the name of, a template. The
     * templates contain the settings, per event type, that the recording will use. The default
     * ones that are shipped with the JDK are located in the {@literal jre/lib/jfr} folder.</p>
     */
    public Builder withSettings(String settings) {
      this.settings = settings;
      return this;
    }

    /**
     * Sets the name of the recording
     *
     * <p>If not specified, a random number will be used.</p>
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Enables gzip compression of the output file
     */
    public Builder enableCompression() {
      this.enableCompression = true;
      return this;
    }

    public JavaFlightRecording build() throws JfrNotAvailable {
      if (outputPath == null) {
        outputPath = Paths.get(name + ".jfr");
      }

      Conf conf = new Conf(outputPath, name, settings, enableCompression);
      return new JavaFlightRecording(conf);
    }
  }
}