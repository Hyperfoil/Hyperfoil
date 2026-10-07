package io.hyperfoil.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Smoke tests for the shaded jars produced by {@code hyperfoil-clustering}.
 * <p>
 * Everything else in the test suite runs off the Maven classpath, where each module keeps its own
 * {@code META-INF/services} files and its own resources. The shade plugin merges all of that into one jar, and the
 * failure mode is silent: a service file that is overwritten rather than concatenated leaves the jar starting
 * normally and then failing to resolve a step, and a dropped resource only surfaces when something asks for it.
 * <p>
 * {@link #shouldRunAWorkloadAndWriteAReportFromTheShadedWrkJar} is the end-to-end half: it forks {@code wrk.jar},
 * which boots an in-VM controller, runs a real benchmark and then renders the HTML report. Rendering the report is
 * the point rather than a bonus - it is the one code path that reads a resource out of the jar through a
 * classloader ({@code report-template-v3.3.html} in {@code ControllerServer}), and inside the shaded jar the
 * thread context classloader can be null, which is why that lookup uses the class' own loader.
 * <p>
 * {@link #shouldShipTheResourcesAndServicesEachShadedJarNeeds} is the cheap half, covering the jars that cannot be
 * driven headlessly - {@code cli.jar} wants a terminal. It only inspects the archives, so it proves the shade
 * configuration is intact, not that the jar runs.
 */
@Tag("io.hyperfoil.test.Benchmark")
public class FatJarTest extends BaseBenchmarkTest {
   /** Injected into the template in place of the stats JSON; if it survives into the output, nothing was rendered. */
   private static final String DATA_PLACEHOLDER = "[/**DATAKEY**/]";
   private static final long WRK_TIMEOUT_MS = 180_000;

   @Test
   public void shouldRunAWorkloadAndWriteAReportFromTheShadedWrkJar(@TempDir Path tmp) throws Exception {
      Path report = tmp.resolve("report.html");
      String url = "http://localhost:" + httpServer.actualPort() + "/";
      // Short, and with the warm up cut down from its 6s default: this is a smoke test, not a measurement.
      List<String> output = run(WRK_TIMEOUT_MS, javaExecutable(), "-jar", shadedJar("wrk").toString(),
            "-c", "4", "-t", "2", "-d", "3s", "--warmup-duration", "1s", "-o", report.toString(), url);
      String printed = String.join("\n", output);

      // Wrk.main throws away the exit code from exec(), so the process is always 0 and the output is the only
      // signal there is.
      assertThat(printed)
            .withFailMessage("wrk.jar did not complete a run:%n%s", printed)
            .contains("Requests/sec:")
            .doesNotContain("Failed to execute command")
            .doesNotContain("ERROR:");
      assertThat(report)
            .withFailMessage("wrk.jar produced no report; the controller could not render one:%n%s", printed)
            .exists();

      String html = Files.readString(report, StandardCharsets.UTF_8);
      assertThat(html).startsWith("<!DOCTYPE html>");
      // The template loaded and the statistics were spliced into it. A 500 from the report endpoint would have
      // left no file at all; a template read straight through would still hold the placeholder.
      assertThat(html)
            .withFailMessage("The report still holds the data placeholder, so no statistics were injected")
            .doesNotContain(DATA_PLACEHOLDER);
      // Only present if the run's all.json really was spliced in.
      assertThat(html).contains("http://hyperfoil.io/run-schema/");
   }

   @Test
   public void shouldShipTheResourcesAndServicesEachShadedJarNeeds() throws IOException {
      Map<String, String> mainClasses = Map.of(
            "cli", "io.hyperfoil.cli.HyperfoilCli",
            "wrk", "io.hyperfoil.cli.commands.Wrk",
            "wrk2", "io.hyperfoil.cli.commands.Wrk2",
            "run", "io.hyperfoil.cli.commands.LoadAndRun");
      for (Map.Entry<String, String> entry : mainClasses.entrySet()) {
         assertShadedJar(entry.getKey(), entry.getValue());
      }
   }

   private static void assertShadedJar(String name, String mainClass) throws IOException {
      try (JarFile jar = new JarFile(shadedJar(name).toFile())) {
         assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class"))
               .as("%s.jar Main-Class", name).isEqualTo(mainClass);

         // The JGroups stacks are read by JChannel from the classpath by name, so a clustered controller or agent
         // launched from the jar dies at startup without them.
         assertThat(jar.getEntry("jgroups-tcp.xml")).as("%s.jar jgroups-tcp.xml", name).isNotNull();
         assertThat(jar.getEntry("jgroups-tcp-agent.xml")).as("%s.jar jgroups-tcp-agent.xml", name).isNotNull();
         assertThat(jar.getEntry("report-template-v3.3.html")).as("%s.jar report template", name).isNotNull();
         assertThat(jar.getEntry("openapi.yaml")).as("%s.jar openapi.yaml", name).isNotNull();

         // Hyperfoil discovers plugins, step builders, controllers and deployers through ServiceLoader. These
         // files exist in several modules, so they are the ones the ServicesResourceTransformer has to merge
         // rather than let the last module win.
         for (String service : List.of(
               "io.hyperfoil.core.api.Plugin",
               "io.hyperfoil.impl.StepCatalogFactory",
               "io.hyperfoil.api.config.StepBuilder",
               "io.hyperfoil.internal.Controller$Factory",
               "io.hyperfoil.api.deployment.Deployer$Factory")) {
            assertThat(jar.getEntry("META-INF/services/" + service)).as("%s.jar %s", name, service).isNotNull();
         }
      }
   }

   /**
    * The jars are built in {@code hyperfoil-clustering}'s package phase, which the reactor runs before this
    * module's tests. Running this module on its own leaves them missing or stale, hence the explicit message.
    */
   private static Path shadedJar(String name) {
      Path jar = Path.of("..", "clustering", "target", name + ".jar").toAbsolutePath().normalize();
      assertThat(jar)
            .withFailMessage("%s has not been built. Run the whole reactor, e.g. "
                  + "mvn clean install -Pbenchmark, rather than this module alone.", jar)
            .exists();
      return jar;
   }

   private static String javaExecutable() {
      return Path.of(System.getProperty("java.home"), "bin", "java").toString();
   }

   private static List<String> run(long timeoutMs, String... command) throws IOException, InterruptedException {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      List<String> lines;
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
         lines = reader.lines().collect(Collectors.toList());
      }
      if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
         process.destroyForcibly();
         throw new AssertionError(String.join(" ", command) + " did not finish within " + timeoutMs + " ms");
      }
      return lines;
   }
}
