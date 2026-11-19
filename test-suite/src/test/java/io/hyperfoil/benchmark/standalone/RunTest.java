package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.benchmark.OutputValidation;
import io.hyperfoil.cli.commands.LoadAndRun;

@Tag("io.hyperfoil.test.Benchmark")
public class RunTest extends BaseBenchmarkTest {

   @Test
   public void testRunMain() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun.main(new String[] { "-PSERVER_PORT=" + httpServer.actualPort(), benchmark });
   }

   @Test
   public void testRun() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testRunMissingBenchmarkArg() {
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] {});
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunBenchmarkNotFound() {
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { "not-found.hf.yaml" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunClusteredRejectsBenchmarkWithoutAgents() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { "--clustered", benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunHtmlOutput(@TempDir Path tempDir) {
      testRunReportExportOutput(tempDir, ".html");
   }

   @Test
   public void testRunJsonOutput(@TempDir Path tempDir) throws Exception {
      Path jsonFile = testRunReportExportOutput(tempDir, ".json");
      OutputValidation.validateJsonContent(jsonFile);
   }

   @Test
   public void testRunCsvOutput(@TempDir Path tempDir) throws Exception {
      Path csvFile = testRunReportExportOutput(tempDir, ".csv");
      assertTrue(csvFile.toString().endsWith(".zip"));
      OutputValidation.validateCsvZipContent(csvFile, tempDir);
   }

   @Test
   public void testRunZipOutput(@TempDir Path tempDir) throws Exception {
      Path zipFile = testRunReportExportOutput(tempDir, ".zip");
      OutputValidation.validateCsvZipContent(zipFile, tempDir);
   }

   @Test
   public void testRunOutputDirectoryGeneratesHtmlReport(@TempDir Path tempDir) throws IOException {
      // --output pointing at an existing directory (no extension) is documented usage:
      // it should write <runId>.html inside that directory, like the "report" command does.
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort(), "--output",
            tempDir.toString() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Run command should succeed.");

      try (var files = Files.list(tempDir)) {
         assertTrue(files.anyMatch(p -> p.toString().endsWith(".html")),
               "Expected an .html report to be created inside " + tempDir);
      }
   }

   @Test
   public void testRunOutputDirectoryWithDotInNameGeneratesHtmlReport(@TempDir Path tempDir) throws IOException {
      // A dot in the directory name (not the file name) must not be mistaken for an extension
      Path dir = tempDir.resolve("my.dir");
      Files.createDirectory(dir);
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort(), "--output",
            dir.toString() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Run command should succeed.");

      try (var files = Files.list(dir)) {
         assertTrue(files.anyMatch(p -> p.toString().endsWith(".html")),
               "Expected an .html report to be created inside " + dir);
      }
   }

   @Test
   public void testRunOutputWithoutExtensionGeneratesHtmlReport(@TempDir Path tempDir) {
      // --output pointing at a file name with no extension should fall back to HTML,
      // not fail with a confusing "Unknown format" error.
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      Path outputFile = tempDir.resolve("report");
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort(), "--output",
            outputFile.toString() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Run command should succeed.");
      assertTrue(outputFile.toFile().exists(), "Expected HTML report to be created at: " + outputFile);
   }

   private Path testRunReportExportOutput(@TempDir Path tempDir, String extension) {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      Path outputFile = tempDir.resolve(UUID.randomUUID() + extension);
      assertFalse(outputFile.toFile().exists());
      LoadAndRun cmd = new LoadAndRun();
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort(), "--output",
            outputFile.toString() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Run command should succeed.");

      if (".csv".equals(extension)) {
         outputFile = Path.of(outputFile.toString().replace(".csv", ".zip"));
         assertTrue(outputFile.toString().endsWith(".zip"));
      }
      assertTrue(outputFile.toFile().exists(), "Expected " + extension + " output file to be created at: " + outputFile);
      return outputFile;
   }
}
