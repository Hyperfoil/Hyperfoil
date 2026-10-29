package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;
import io.hyperfoil.cli.commands.WrkAbstract;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.model.RequestStats;

@Tag("io.hyperfoil.test.Benchmark")
// If you need to debug use "-Dio.hyperfoil.controller.log.level=debug" VM option and read the file target/hyperfoil/hyperfoil.local.log
public class WrkTest extends BaseWrkBenchmarkTest {

   protected static final Logger log = LogManager.getLogger(WrkTest.class);

   @Test
   public void testWrk() {
      Wrk cmd = new Wrk();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/highway" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testWrkUnpredictable() {
      Wrk cmd = new Wrk();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testFailFastWrk() {
      Wrk cmd = new Wrk();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "--latency", "--timeout", "1s",
            "nonExistentHost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
   }

   @Test
   public void testWrk2() {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/highway" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testWrk2Unpredictable() {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }

   @Test
   public void testWrkReportOutput(@TempDir Path tempDir) {
      testWrk2ReportExportOutput(tempDir, ".html");
   }

   @Test
   public void testWrkJsonOutput(@TempDir Path tempDir) throws Exception {
      Path jsonFile = testWrk2ReportExportOutput(tempDir, ".json");
      validateJsonContent(jsonFile);
   }

   @Test
   public void testWrkCsvOutput(@TempDir Path tempDir) throws Exception {
      Path csvFile = testWrk2ReportExportOutput(tempDir, ".csv");
      validateCsvZipContent(csvFile, tempDir);
   }

   @Test
   public void testWrkZipOutput(@TempDir Path tempDir) throws Exception {
      Path zipFile = testWrk2ReportExportOutput(tempDir, ".zip");
      validateCsvZipContent(zipFile, tempDir);
   }

   private Path testWrk2ReportExportOutput(@TempDir Path tempDir, String extension) {
      Path reportFile = tempDir.resolve(UUID.randomUUID() + extension);
      assertFalse(reportFile.toFile().exists());
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "--output",
            reportFile.toString(), "localhost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result, "Wrk2 command should succeed.");
      assertTrue(reportFile.toFile().exists(), "Expected " + extension + " report file to be created at: " + reportFile);
      return reportFile;
   }

   @Test
   public void testWrkStats() throws CommandNotFoundException {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "10s", "-R", "1", "--latency", "--timeout", "2s",
            "localhost:" + httpServer.actualPort() + "/1s" });

      // Due to the nature of instances being created in runtime the only way to retrieve is by using the commandRegistry
      CommandContainer<HyperfoilCommandInvocation> commandContainer = cmd.getCommandRegistry().getCommand("wrk2", null);
      ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
      Wrk2.Wrk2Command wrk2Command = (Wrk2.Wrk2Command) processedCommand.getCommand();
      WrkAbstract.WrkCommandResult wrkCommandResult = wrk2Command.getWrkCommandResult();

      // assert
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      assertFalse(wrkCommandResult.getRequestStatisticsResponse().statistics.isEmpty());
      for (RequestStats requestStats : wrkCommandResult.getRequestStatisticsResponse().statistics) {
         assertTrue(requestStats.failedSLAs.isEmpty());
      }
   }

   @Test
   public void testWrk2CalibrationDoesNotCancelTestPhase() throws CommandNotFoundException {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "60000", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/highway" });

      CommandContainer<HyperfoilCommandInvocation> commandContainer = cmd.getCommandRegistry().getCommand("wrk2", null);
      ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
      Wrk2.Wrk2Command wrk2Command = (Wrk2.Wrk2Command) processedCommand.getCommand();
      WrkAbstract.WrkCommandResult wrkCommandResult = wrk2Command.getWrkCommandResult();

      assertEquals(CommandResult.SUCCESS.getResultValue(), result,
            "wrk2 should succeed even when calibration SLA fails — wrk2 does not enforce SLA");
      assertFalse(wrkCommandResult.getRequestStatisticsResponse().statistics.isEmpty());
      boolean testPhaseFound = wrkCommandResult.getRequestStatisticsResponse().statistics.stream()
            .anyMatch(rs -> "test".equals(rs.phase));
      assertTrue(testPhaseFound,
            "Test phase must have statistics even when calibration fails SLA validation");
   }

   @Test
   public void testWrk2HighLoad() throws CommandNotFoundException {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20000", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/unpredictable" });

      // Due to the nature of instances being created in runtime the only way to retrieve is by using the commandRegistry
      CommandContainer<HyperfoilCommandInvocation> commandContainer = cmd.getCommandRegistry().getCommand("wrk2", null);
      ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
      Wrk2.Wrk2Command wrk2Command = (Wrk2.Wrk2Command) processedCommand.getCommand();
      WrkAbstract.WrkCommandResult wrkCommandResult = wrk2Command.getWrkCommandResult();

      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      assertFalse(wrkCommandResult.getRequestStatisticsResponse().statistics.isEmpty());

      boolean failedSLA = false;
      for (RequestStats requestStats : wrkCommandResult.getRequestStatisticsResponse().statistics) {
         failedSLA = !requestStats.failedSLAs.isEmpty();
         if (failedSLA) {
            break;
         }
      }
      // we are expecting at least one failed SLA because of the high rate
      assertTrue(failedSLA);
   }

   @Test
   public void testWrk2ReqSec() throws CommandNotFoundException {
      int seconds = 5;
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-t", "1", "-c", "5", "-d", seconds + "s", "-R", "1000", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/50ms" });

      CommandContainer<HyperfoilCommandInvocation> commandContainer = cmd.getCommandRegistry().getCommand("wrk2", null);
      ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
      Wrk2.Wrk2Command wrk2Command = (Wrk2.Wrk2Command) processedCommand.getCommand();
      WrkAbstract.WrkCommandResult wrkCommandResult = wrk2Command.getWrkCommandResult();

      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      List<StatisticsSummary> series = wrkCommandResult.getSeries();
      assertNotNull(series, "Series list should not be null");
      int size = series.size();
      assertTrue(size >= (seconds - 1) && size <= (seconds + 1), "Expected ~" + seconds + " buckets, but got: " + size);
   }

   private void validateJsonContent(Path jsonFile) throws Exception {
      String content = Files.readString(jsonFile);
      try {
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(content);
         assertTrue(root.has("stats"), "Expected JSON to contain 'stats' key");
         assertTrue(root.get("stats").isArray(), "Expected 'stats' to be an array");
      } catch (Exception e) {
         log.error("Invalid json content:\n" + content);
         throw e;
      }

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
