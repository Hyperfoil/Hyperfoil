package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.LoadAndRun;
import io.vertx.core.json.JsonObject;

@Tag("io.hyperfoil.test.Benchmark")
public class RunTest extends BaseBenchmarkTest {

   @Test
   public void testRunMain() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      int result = LoadAndRun.run(new String[] { "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testRun() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      int result = new LoadAndRun(false)
            .exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testRunMissingBenchmarkArg() {
      int result = new LoadAndRun(false).exec(new String[0]);
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunBenchmarkNotFound() {
      int result = new LoadAndRun(false).exec(new String[] { "not-found.hf.yaml" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunClusteredRejectsBenchmarkWithoutAgents() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      int result = new LoadAndRun(true)
            .exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunFailsOnValidationErrorsOnlyWhenRequested(@TempDir Path tempDir)
         throws IOException {
      Path benchmarkFile = tempDir.resolve("invalid-response.hf.yaml");
      // the test server always responds with 200, so every response is invalid for this benchmark
      Files.writeString(benchmarkFile, """
            name: invalid-response
            http:
              protocol: http
              host: localhost
              port: !param SERVER_PORT
            phases:
            - test:
                atOnce:
                  users: 1
                  duration: 0s
                  scenario:
                    initialSequences:
                    - request:
                      - httpRequest:
                          GET: /foo
                          handler:
                            status:
                              range: 5xx
            """);
      String benchmark = benchmarkFile.toString();
      String port = "-PSERVER_PORT=" + httpServer.actualPort();
      Path resultFile = tempDir.resolve("failed-result.json");

      assertEquals(CommandResult.SUCCESS.getResultValue(),
            new LoadAndRun(false).exec(new String[] { port, benchmark }));
      assertEquals(CommandResult.FAILURE.getResultValue(),
            new LoadAndRun(false)
                  .exec(new String[] { "--fail-on-errors", "--export", resultFile.toString(), port, benchmark }));
      assertFalse(new JsonObject(Files.readString(resultFile)).isEmpty());
   }

   @Test
   public void testRunRejectsUnknownExportFormat() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");

      int result = new LoadAndRun(false)
            .exec(new String[] { "--export-format", "XML", "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });

      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunExportsJson(@TempDir Path tempDir) throws IOException {
      Path resultFile = tempDir.resolve("result file.json");
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");

      int result = new LoadAndRun(false)
            .exec(new String[] { "--export", resultFile.toString(), "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });

      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      assertTrue(Files.isRegularFile(resultFile));
      assertFalse(new JsonObject(Files.readString(resultFile)).isEmpty());
   }

   @Test
   public void testRunFailsWhenExportCannotBeWritten(@TempDir Path tempDir) {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      Path resultFile = tempDir.resolve("missing").resolve("result.json");

      int result = new LoadAndRun(false)
            .exec(new String[] { "--export", resultFile.toString(), "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });

      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunExportsCsv(@TempDir Path tempDir) throws IOException {
      Path resultFile = tempDir.resolve("result.zip");
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");

      int result = new LoadAndRun(false).exec(new String[] { "--export", resultFile.toString(), "--export-format", "CSV",
            "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });

      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      byte[] content = Files.readAllBytes(resultFile);
      assertTrue(content.length >= 2 && content[0] == 'P' && content[1] == 'K');
   }
}
