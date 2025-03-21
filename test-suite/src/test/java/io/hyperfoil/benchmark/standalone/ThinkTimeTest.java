package io.hyperfoil.benchmark.standalone;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

@Tag("io.hyperfoil.test.Benchmark")
public class ThinkTimeTest extends BaseBenchmarkTest {
   private static final Logger log = LogManager.getLogger(ThinkTimeTest.class);
   private static final long benchmarkDuration = 1000;

   // parameters source
   private static Stream<Arguments> thinkTimesConfigs() {
      return Stream.of(
            Arguments.of("phase a", 1),
            Arguments.of("phase a", TimeUnit.MILLISECONDS.toNanos(50)),
            Arguments.of("phase b", TimeUnit.MILLISECONDS.toNanos(500)),
            Arguments.of("phase c", TimeUnit.MILLISECONDS.toNanos(1000)));
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> {
         req.response().end("hello from server");
      };
   }

   @ParameterizedTest
   @MethodSource("thinkTimesConfigs")
   public void testThinkTime(String phase1, long delayNs) {
      BenchmarkBuilder builder = createBuilder(phase1, delayNs);
      Benchmark benchmark = builder.build();

      // check think time is correctly setup
      Phase mainPhase = benchmark.phases().stream().filter(p -> p.name.equals(phase1)).findAny().orElseThrow();
      // beforeSync, prepareHttpReq, sendHttpReq, afterSync, scheduleDelay, awaitDelay
      assertEquals(6, mainPhase.scenario().sequences()[0].steps().length);

      TestStatistics statisticsConsumer = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, statisticsConsumer, null, null);
      runner.run();
      long now = System.currentTimeMillis();

      // check start time
      PhaseInstance phase = runner.instances().get(phase1);
      long expectedDuration = phase.definition().duration();
      long remaining = expectedDuration - (now - phase.absoluteStartTime());
      log.debug("Remaining = {}", remaining);

      StatisticsSnapshot stats = statisticsConsumer.stats().get("request");
      assertEquals(0, stats.invalid);
      // the thinkTime starts just after the request is completed
      assertTrue(remaining < 0);
   }

   /**
    * create a builder with two phases where the first one should start with the second one after a provided delay
    */
   private BenchmarkBuilder createBuilder(String firstPhase, long thinkTime) {
      // @formatter:off
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("thinkTime " + new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date()))
            .addPlugin(HttpPluginBuilder::new).http()
            .host("localhost").port(httpServer.actualPort())
            .sharedConnections(50)
            .endHttp().endPlugin()
            .threads(1);

      builder.addPhase(firstPhase).constantRate(50)
            .duration(benchmarkDuration)
            .maxSessions(50)
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .path("/")
                     .timeout("60s")
                     .handler()
                        .rawBytes(new TransferSizeRecorder("transfer"))
                     .endHandler()
                  .endStep()
                  .step(SC)
                     .thinkTime(thinkTime, TimeUnit.NANOSECONDS)
                  .endStep()
               .endSequence();
      // @formatter:on

      return builder;
   }

   public static class TestStatistics implements StatisticsCollector.StatisticsConsumer {
      private final Map<String, StatisticsSnapshot> stats = new HashMap<>();

      @Override
      public void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown) {
         log.debug("Adding stats for {}/{}/{} - #{}: {} requests {} responses", phase, stepId, metric,
               snapshot.sequenceId, snapshot.requestCount, snapshot.responseCount);
         stats.computeIfAbsent(metric, n -> new StatisticsSnapshot()).add(snapshot);
      }

      public Map<String, StatisticsSnapshot> stats() {
         return stats;
      }
   }
}
