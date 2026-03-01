package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;
import io.hyperfoil.cli.commands.WrkAbstract;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.model.RequestStats;

@Tag("io.hyperfoil.test.Benchmark")
// If you need to debug use "-Dio.hyperfoil.controller.log.level=debug" VM option
public class WrkTest extends BaseWrkBenchmarkTest {

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
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
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
      Path reportFile = tempDir.resolve(UUID.randomUUID() + ".html");
      assertFalse(reportFile.toFile().exists());
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "--output",
            reportFile.toString(), "localhost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
      assertTrue(reportFile.toFile().exists());
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
   public void testWrk2HighLoad() throws CommandNotFoundException {
      Wrk2 cmd = new Wrk2();
      int result = cmd.exec(new String[] { "-c", "10", "-d", "20s", "-R", "20000", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/unpredictable" });

      // Due to the nature of instances being created in runtime the only way to retrieve is by using the commandRegistry
      CommandContainer<HyperfoilCommandInvocation> commandContainer = cmd.getCommandRegistry().getCommand("wrk2", null);
      ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
      Wrk2.Wrk2Command wrk2Command = (Wrk2.Wrk2Command) processedCommand.getCommand();
      WrkAbstract.WrkCommandResult wrkCommandResult = wrk2Command.getWrkCommandResult();

      // assert
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
      assertFalse(wrkCommandResult.getRequestStatisticsResponse().statistics.isEmpty());
      for (RequestStats requestStats : wrkCommandResult.getRequestStatisticsResponse().statistics) {
         assertFalse(requestStats.failedSLAs.isEmpty());
      }
   }
}
