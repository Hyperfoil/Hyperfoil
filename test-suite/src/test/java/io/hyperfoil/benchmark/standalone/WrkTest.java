package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.container.CommandContainer;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.registry.CommandRegistry;
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
   public void testWrk2HighLoad() {
      final AtomicReference<Wrk2.Wrk2Command> command = new AtomicReference<>();
      Wrk2 cmd = new Wrk2() {
         @Override
         public void handleRegistry(CommandRegistry<HyperfoilCommandInvocation> commandRegistry)
               throws CommandNotFoundException {
            CommandContainer<HyperfoilCommandInvocation> commandContainer = commandRegistry.getCommand("wrk2", null);
            ProcessedCommand processedCommand = commandContainer.getParser().getProcessedCommand();
            command.set((Wrk2.Wrk2Command) processedCommand.getCommand());
         }
      };
      int result = cmd.exec(new String[] { "-c", "10", "-d", "20s", "-R", "20000", "--latency", "--timeout", "1s",
            "localhost:" + httpServer.actualPort() + "/unpredictable" });
      assertEquals(CommandResult.FAILURE.getResultValue(), result);
      WrkAbstract.WrkCommandResult wrkCommandResult = command.get().getWrkCommandResult();
      assertFalse(wrkCommandResult.getRequestStatisticsResponse().statistics.isEmpty());
      for (RequestStats requestStats : wrkCommandResult.getRequestStatisticsResponse().statistics) {
         assertFalse(requestStats.failedSLAs.isEmpty());
      }
   }
}
