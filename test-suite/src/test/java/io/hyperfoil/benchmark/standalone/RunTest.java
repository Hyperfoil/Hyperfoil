package io.hyperfoil.benchmark.standalone;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
}
