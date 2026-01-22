package io.hyperfoil.scenario;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.cli.commands.WrkScenarioPhaseConfig;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;

public class WrkScenarioTest extends BaseBenchmarkTest {

   protected final Logger log = LogManager.getLogger(getClass());

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      Router router = Router.router(vertx);
      router.route("/10s").handler(ctx -> {
         ctx.vertx().setTimer(10_000, id -> {
            ctx.response().end("10s");
         });
      });
      return router;
   }

   @Test
   public void testWrk() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/foo/bar";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(6, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   public void testFailFastWrk() throws URISyntaxException {

      String url = "nonExistentHost:" + httpServer.actualPort() + "/foo/bar";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(6, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertFalse(phaseStats.containsKey("calibration"),
            "Stats must not have values for the 'calibration' phase because the host is invalid");
      Assertions.assertFalse(phaseStats.containsKey("test"),
            "Stats must not have values for the 'test' phase because the host is invalid");
   }

   @Test
   public void testWrk2() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/foo/bar";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrk2Scenario(6, 5, url, 20, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   /*
   @formatter:off
   This test expects approximately 10 in-flight requests per phase.
   Configuration:
     - Service time: 10 seconds per request
     - Calibration duration: 5 seconds
     - Test duration: 5 seconds
     - Concurrent connections: 10
     - Threads: 1
   Note: Since service time (10s) exceeds test duration (5s), requests will still be in-flight when the test completes.

   Here is a comparison between the original wrk and the Hyperfoil wrk implementation before this PR
   -----
   $ wrk -c 10 -t 1 -d 5s http://localhost:33193/10s
      Running 5s test @ http://localhost:33193/10s
        1 threads and 10 connections
        Thread Stats   Avg      Stdev     Max   +/- Stdev
          Latency     0.00us    0.00us   0.00us    -nan%
          Req/Sec     0.00      0.00     0.00      -nan%
        0 requests in 5.02s, 0.00B read
      Requests/sec:      0.00
      Transfer/sec:       0.00B
   -----
   $ jbang wrk@hyperfoil -c 10 -t 1 -d 5s http://localhost:35815/10s
      Running 5s test @ http://localhost:35815/10s
        1 threads and 10 connections
        Thread Stats   Avg      Stdev     Max   +/- Stdev
          Latency        0ns       0ns      0ns    0.00%
          Req/Sec    10.00      0.00    10.00    100.00
        10 requests in 5.008s, 380.00B  read
      Requests/sec: 2.00
      Transfer/sec:  75.88B
   ----
   @formatter:on
   */
   @Test
   public void testWrkInFlightRequests() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/10s";
      int connections = 10;
      int threads = 1;
      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(5, 5, url, 20, connections, threads);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      StatisticsSnapshot calibrationStats = phaseStats.get("calibration").get("request");
      StatisticsSnapshot testStats = phaseStats.get("test").get("request");
      Assertions.assertEquals(connections * threads, calibrationStats.requestCount);
      Assertions.assertEquals(connections * threads, testStats.requestCount);
      Assertions.assertEquals(connections * threads, calibrationStats.inFlightRequests);
      Assertions.assertEquals(connections * threads, testStats.inFlightRequests);
      Assertions.assertEquals(0, calibrationStats.responseCount);
      Assertions.assertEquals(0, testStats.responseCount);
      Assertions.assertEquals(0, calibrationStats.requestTimeouts);
      Assertions.assertEquals(0, testStats.requestTimeouts);
   }

   @Test
   public void testWrk2HighLoad() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/foo/bar";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrk2Scenario(6, 5, url, 20000, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   private BaseScenarioTest.TestStatistics runWrkScenario(int calibrationDuration, int testDuration, String url,
         int timeout, int connections, int threads) throws URISyntaxException {
      return runScenario(calibrationDuration, testDuration, url, timeout, connections, threads, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            return WrkScenarioPhaseConfig.wrkPhaseConfig(catalog, connections);
         }
      });
   }

   private BaseScenarioTest.TestStatistics runWrk2Scenario(int calibrationDuration, int testDuration, String url,
         int rate, int timeout, int connections, int threads) throws URISyntaxException {
      return runScenario(calibrationDuration, testDuration, url, timeout, connections, threads, () -> new WrkScenario() {
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
   private BaseScenarioTest.TestStatistics runScenario(int calibrationDuration, int testDuration, String url, int timeout,
         int connections, int threads, Supplier<WrkScenario> fn) throws URISyntaxException {
      boolean enableHttp2 = false;
      boolean useHttpCache = false;
      Map<String, String> agent = null;
      String[][] parsedHeaders = null;

      WrkScenario wrkScenario = fn.get();

      BenchmarkBuilder builder = wrkScenario.getBenchmarkBuilder("my-test", url, enableHttp2, connections, useHttpCache,
            threads, agent, calibrationDuration + "s", testDuration + "s", parsedHeaders, timeout + "s");

      BaseScenarioTest.TestStatistics statisticsConsumer = new BaseScenarioTest.TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();
      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
      return statisticsConsumer;
   }
}
