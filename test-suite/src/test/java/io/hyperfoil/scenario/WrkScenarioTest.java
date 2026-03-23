package io.hyperfoil.scenario;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.benchmark.BaseWrkBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.cli.commands.WrkScenarioPhaseConfig;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.netty.buffer.ByteBuf;

public class WrkScenarioTest extends BaseWrkBenchmarkTest {

   protected final Logger log = LogManager.getLogger(getClass());

   @Test
   public void testWrk() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(6, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
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
      assertTrue(phaseStats.containsKey("calibration"), "Stats must have values for the 'calibration' phase");
      assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   public void testWrkWarmupDuration() throws URISyntaxException {

      String url = "localhost:" + httpServer.actualPort() + "/highway";

      BaseScenarioTest.TestStatistics statisticsConsumer = runWrkScenario(0, 5, url, 1, 10, 2);
      Map<String, Map<String, StatisticsSnapshot>> phaseStats = statisticsConsumer.phaseStats();
      Assertions.assertFalse(phaseStats.containsKey("calibration"),
            "Stats must not have values for the 'calibration' phase because calibration duration is 0");
      assertTrue(phaseStats.containsKey("test"), "Stats must have values for the 'test' phase");
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

      class RequestCounterHandler implements RawBytesHandler {

         private final AtomicInteger counter = new AtomicInteger();
         long[] startTimesNs = new long[rate * duration];

         @Override
         public void onRequest(Request request, ByteBuf buf, int offset, int length) {
            // ignore outside the range
            if (counter.get() < startTimesNs.length) {
               int index = counter.getAndIncrement();
               startTimesNs[index] = request.startTimestampNanos();
            }
         }

         @Override
         public void onResponse(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {

         }
      }

      RequestCounterHandler handler = new RequestCounterHandler();
      runWrk2Scenario(0, duration, url, rate, 1, 10, 2, handler);

      int maxEventsInBucket = 0;
      int left = 0;
      // begin - unrealistic
      // target is 2,000 RPS, your ideal bucket size is 500,000 ns
      // Real-world systems have scheduling jitter, network delays, and timing variations
      long bucketWindowNs = 500_000;
      // it will be super hard to be on 1, 2, 4
      int expectedMaxEventsPerBucket = 1;
      // end - unrealistic

      long[] validStartTimesNs = Arrays.copyOf(handler.startTimesNs, handler.counter.get());
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
      }, null);
   }

   private BaseScenarioTest.TestStatistics runWrk2Scenario(int warmupDuration, int testDuration, String url,
         int rate, int timeout, int connections, int threads) throws URISyntaxException {
      return this.runWrk2Scenario(warmupDuration, testDuration, url, rate, timeout, connections, threads, null);
   }

   private BaseScenarioTest.TestStatistics runWrk2Scenario(int warmupDuration, int testDuration, String url,
         int rate, int timeout, int connections, int threads, RawBytesHandler otherHandler) throws URISyntaxException {
      return runScenario(warmupDuration, testDuration, url, timeout, connections, threads, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            return WrkScenarioPhaseConfig.wrk2PhaseConfig(catalog, phaseType, durationMs, rate);
         }
      }, otherHandler);
   }

   /**
    *
    * @param timeout value should be in second
    */
   private BaseScenarioTest.TestStatistics runScenario(int warmupDuration, int testDuration, String url, int timeout,
         int connections, int threads, Supplier<WrkScenario> fn, RawBytesHandler otherHandler) throws URISyntaxException {
      boolean enableHttp2 = false;
      boolean useHttpCache = false;
      Map<String, String> agent = null;
      String[][] parsedHeaders = null;

      WrkScenario wrkScenario = fn.get();

      BenchmarkBuilder builder = wrkScenario.getBenchmark("my-test", url, enableHttp2, connections, useHttpCache,
            threads, agent, warmupDuration + "s", testDuration + "s", parsedHeaders, timeout + "s", otherHandler);

      BaseScenarioTest.TestStatistics statisticsConsumer = new BaseScenarioTest.TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();
      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
      return statisticsConsumer;
   }
}
