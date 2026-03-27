package io.hyperfoil.scenario;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.cli.commands.WrkScenarioPhaseConfig;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;

public class WrkScenarioTest extends BaseWrkBenchmarkTest {

   protected final Logger log = LogManager.getLogger(getClass());

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

   @Test
   public void testBurstiness() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";
      // warmup the code
      runWrkScenario(10, 10, url, 1, 10, 2);

      // https://github.com/Hyperfoil/Hyperfoil/issues/342
      // should be greater than 1000
      int rate = 2000;
      int duration = 10;
      int totalExpectedRequests = rate * duration;

      class TimestampStep implements Step {
         private final AtomicInteger counter = new AtomicInteger();
         final AtomicLongArray startTimesNs = new AtomicLongArray(totalExpectedRequests);

         @Override
         public boolean invoke(Session session) {
            int index = counter.getAndIncrement();
            if (index < startTimesNs.length()) {
               startTimesNs.set(index, System.nanoTime());
            }
            return true;
         }
      }

      TimestampStep timestampStep = new TimestampStep();

      // Build the benchmark explicitly for this test
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("burstiness-test")
            .threads(2);

      builder.addPlugin(HttpPluginBuilder::new)
            .http()
            .host("localhost")
            .port(httpServer.actualPort())
            .sharedConnections(10)
            .endHttp();

      long durationMs = duration * 1000L;
      long timeoutMs = 1000L; // 1 second timeout

      // Explicitly set duration and maxDuration here
      PhaseBuilder<?> phaseBuilder = WrkScenarioPhaseConfig
            .wrk2PhaseConfig(builder.addPhase("test"), WrkScenario.PhaseType.test, durationMs, rate)
            .duration(durationMs)
            .maxDuration(durationMs + timeoutMs);

      phaseBuilder.scenario()
            .maxRequests(1)
            .maxSequences(1)
            .initialSequence("request")
            .step(timestampStep) // Inject our timestamp recorder
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
            .path("/highway")
            .endStep()
            .endSequence();

      BaseScenarioTest.TestStatistics statisticsConsumer = new BaseScenarioTest.TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      runner.run();

      int maxEventsInBucket = 0;
      int left = 0;

      // At 2,000 RPS, we expect 2 requests/ms, or 20 requests per 10ms window.
      // However, CI environments frequently experience GC pauses or CPU stealing.
      // If the test thread freezes for 100ms, Hyperfoil will fire a "catch-up burst"
      // of 200 requests instantly when it wakes up to maintain the target rate.
      long bucketWindowNs = 10_000_000; // 10ms sliding window
      // We set the failure threshold to 200 to tolerate up to 100ms of CI jitter
      // while still catching genuinely broken rate-limiting (e.g., thousands of requests at once).
      int expectedMaxEventsPerBucket = 200;

      int actualRecorded = Math.min(timestampStep.counter.get(), timestampStep.startTimesNs.length());
      long[] validStartTimesNs = new long[actualRecorded];
      for (int i = 0; i < actualRecorded; i++) {
         validStartTimesNs[i] = timestampStep.startTimesNs.get(i);
      }
      Arrays.sort(validStartTimesNs);

      for (int right = 0; right < validStartTimesNs.length; right++) {
         while (validStartTimesNs[right] - validStartTimesNs[left] >= bucketWindowNs) {
            left++;
         }
         int currentEventsInBucket = right - left + 1;
         if (currentEventsInBucket > maxEventsInBucket) {
            maxEventsInBucket = currentEventsInBucket;
         }
      }

      assertTrue(maxEventsInBucket <= expectedMaxEventsPerBucket,
            String.format("Expected a maximum of %d events within any %dns window, but found %d.",
                  expectedMaxEventsPerBucket, bucketWindowNs, maxEventsInBucket));
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
