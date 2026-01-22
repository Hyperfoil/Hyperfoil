package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;

@Tag("io.hyperfoil.test.Benchmark")
// If you need to debug use "-Dio.hyperfoil.controller.log.level=debug" VM option
public class WrkTest extends BaseWrkBenchmarkTest {

   @Test
   public void testWrk() {
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
}
