package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
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
      LoadAndRun cmd = new LoadAndRun(false);
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testRunMissingBenchmarkArg() {
      LoadAndRun cmd = new LoadAndRun(false);
      int result = cmd.exec(new String[] {});
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunBenchmarkNotFound() {
      LoadAndRun cmd = new LoadAndRun(false);
      int result = cmd.exec(new String[] { "not-found.hf.yaml" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunClusteredRejectsBenchmarkWithoutAgents() {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      LoadAndRun cmd = new LoadAndRun(true);
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testRunHtmlOutput(@TempDir Path tempDir) {
      testRunReportExportOutput(tempDir, ".html");
   }

   @Test
   public void testRunJsonOutput(@TempDir Path tempDir) throws Exception {
      Path jsonFile = testRunReportExportOutput(tempDir, ".json");
      validateJsonContent(jsonFile);
   }

   @Test
   public void testRunCsvOutput(@TempDir Path tempDir) throws Exception {
      Path csvFile = testRunReportExportOutput(tempDir, ".csv");
      validateCsvZipContent(csvFile, tempDir);
   }

   @Test
   public void testRunZipOutput(@TempDir Path tempDir) throws Exception {
      Path zipFile = testRunReportExportOutput(tempDir, ".zip");
      validateCsvZipContent(zipFile, tempDir);
   }

   private Path testRunReportExportOutput(@TempDir Path tempDir, String extension) {
      String benchmark = getBenchmarkPath("scenarios/httpRequestParameterized.hf.yaml");
      Path outputFile = tempDir.resolve(UUID.randomUUID() + extension);
      assertFalse(outputFile.toFile().exists());
      LoadAndRun cmd = new LoadAndRun(false);
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort(), "--output",
            outputFile.toString() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Run command should succeed.");
      assertTrue(outputFile.toFile().exists(), "Expected " + extension + " output file to be created at: " + outputFile);
      return outputFile;
   }

   private void validateJsonContent(Path jsonFile) throws Exception {
      String content = Files.readString(jsonFile);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(content);
      assertTrue(root.has("stats"), "Expected JSON to contain 'stats' key");
      assertTrue(root.get("stats").isArray(), "Expected 'stats' to be an array");
   }

   private void validateCsvZipContent(Path zipFile, Path extractDir) throws IOException {
      byte[] content = Files.readAllBytes(zipFile);
      assertTrue(content.length > 0, "Expected zip file to have content");
      assertTrue(content[0] == 0x50 && content[1] == 0x4B, "Expected zip file format (PK signature)");

      Path unzipDir = extractDir.resolve("unzip_" + UUID.randomUUID());
      Files.createDirectories(unzipDir);

      try (ZipFile zf = new ZipFile(zipFile.toFile())) {
         zf.stream().forEach(entry -> {
            try {
               Path entryPath = unzipDir.resolve(entry.getName());
               if (entry.isDirectory()) {
                  Files.createDirectories(entryPath);
               } else {
                  Files.createDirectories(entryPath.getParent());
                  Files.copy(zf.getInputStream(entry), entryPath);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }

      var csvFiles = Files.list(unzipDir)
            .filter(p -> p.toString().endsWith(".csv"))
            .toList();
      assertTrue(!csvFiles.isEmpty(), "Expected at least one CSV file in the zip archive");

      for (Path csvFile : csvFiles) {
         String csvContent = Files.readString(csvFile);
         assertTrue(csvContent.length() > 0, "Expected CSV file to have content");
         assertTrue(csvContent.contains(","), "Expected CSV file to contain comma separators");
      }
   }
}
