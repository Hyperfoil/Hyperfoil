package io.hyperfoil.benchmark.clustering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.aesh.command.CommandResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.LoadAndRun;
import io.hyperfoil.internal.Properties;

@Tag("io.hyperfoil.test.Benchmark")
public class ClusteredLoadAndRunTest extends BaseBenchmarkTest {

   @Test
   public void testClusteredBenchmarkWithAgents() {
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty("jgroups.bind.address", "127.0.0.1");
      System.setProperty("jgroups.join_timeout", "15000");

      String benchmark = getBenchmarkPath("scenarios/clusteredHttp.hf.yaml");
      LoadAndRun cmd = new LoadAndRun(true);
      int result = cmd.exec(new String[] { benchmark, "-PSERVER_PORT=" + httpServer.actualPort() });
      assertEquals(CommandResult.SUCCESS.getResultValue(), result);
   }
}
