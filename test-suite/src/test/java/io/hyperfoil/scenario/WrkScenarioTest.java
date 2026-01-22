package io.hyperfoil.scenario;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.cli.commands.WrkScenarioPhaseConfig;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.BaseScenarioTest;

public class WrkScenarioTest extends BaseWrkBenchmarkTest {

   private final Logger log = LogManager.getLogger(getClass());

   @Test
   @Disabled("Issue #626: wrk2 fail with high load - scenario where timeout is 2s")
   // This test can be flaky if Hyperfoil is in a state where calibration phase is able to release the connections back to the pool
   // The current configuration with TRACE logs enabled slow down a lot Hyperfoil
   public void wrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/500ms";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrk2Scenario(6, 20, url, 50000, 2, 10, 2);

      Assertions.assertTrue(statisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(statisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   @Disabled("Issue #638: NPE: Cannot read field session because this.request is null")
   public void wrk2SuperSlowServer() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/500ms";
      runWrk2Scenario(6, 20, url, 50000, 2, 10, 2);
   }

   @Test
   public void wrkRequestsMustBeLowerThanWrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/500ms";

      BaseScenarioTest.TestStatistics wrkStatisticsConsumer = runWrkScenario(6, 20, url, 2, 10, 2);
      BaseScenarioTest.TestStatistics wrk2StatisticsConsumer = runWrk2Scenario(6, 20, url, 20000, 2, 10, 2);

      Assertions.assertTrue(wrkStatisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(wrkStatisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");
      Assertions.assertTrue(wrk2StatisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(wrk2StatisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");

      Assertions.assertTrue(wrk2StatisticsConsumer.stats().get("test").requestCount <= wrkStatisticsConsumer
            .stats().get("test").requestCount);
   }

   @Test
   public void testWrk() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(6, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   public void testFailFastWrk() throws URISyntaxException {

      String url = "nonExistentHost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(6, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertFalse(phaseStats.containsKey("calibration"),
            "Stats must not have values for the 'calibration' phase because the host is invalid");
      Assertions.assertFalse(phaseStats.containsKey("test"),
            "Stats must not have values for the 'test' phase because the host is invalid");
   }

   @Test
   public void testWrk2() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrk2Scenario(6, 5, url, 20, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   public void testWrkWarmupDuration() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(0, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertFalse(phaseStats.containsKey("calibration"),
            "Stats must not have values for the 'calibration' phase because calibration duration is 0");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   private BaseScenarioTest.TestStatistics runWrkScenario(int warmupDuration, int testDuration, String url,
         int timeout, int connections, int threads) throws URISyntaxException {
      return runScenario(warmupDuration, testDuration, url, timeout, connections, threads, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            return WrkScenarioPhaseConfig.wrkPhaseConfig(catalog, connections);
         }
      });
   }

   private BaseScenarioTest.TestStatistics runWrk2Scenario(int warmupDuration, int testDuration, String url,
         int rate, int timeout, int connections, int threads) throws URISyntaxException {
      return runScenario(warmupDuration, testDuration, url, timeout, connections, threads, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            return WrkScenarioPhaseConfig.wrk2PhaseConfig(catalog, phaseType, durationMs, rate);
         }
      });
   }

   /**
    *
    * @param timeout value should be in second
    */
   private BaseScenarioTest.TestStatistics runScenario(int warmupDuration, int testDuration, String url, int timeout,
         int connections, int threads, Supplier<WrkScenario> fn) throws URISyntaxException {
      boolean enableHttp2 = false;
      boolean useHttpCache = false;
      Map<String, String> agent = null;
      String[][] parsedHeaders = null;

      WrkScenario wrkScenario = fn.get();

      BenchmarkBuilder builder = wrkScenario.getBenchmark("my-test", url, enableHttp2, connections, useHttpCache,
            threads, agent, warmupDuration + "s", testDuration + "s", parsedHeaders, timeout + "s");

      BaseScenarioTest.TestStatistics statisticsConsumer = new BaseScenarioTest.TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();
      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
      return statisticsConsumer;
   }
}
