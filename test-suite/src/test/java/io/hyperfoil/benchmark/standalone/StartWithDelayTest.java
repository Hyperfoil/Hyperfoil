package io.hyperfoil.benchmark.standalone;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.PhaseReferenceDelay;
import io.hyperfoil.api.config.RelativeIteration;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@Category(io.hyperfoil.test.Benchmark.class)
@RunWith(VertxUnitRunner.class)
public class StartWithDelayTest extends BaseBenchmarkTest {
   protected static final Logger log = LogManager.getLogger(StartWithDelayTest.class);

   @Parameterized.Parameters
   public static Collection<Object[]> data() {
      return Arrays.asList(new Object[][] {
            { "additional", "steady", 0 }, { "additional", "steady", 10000 }
      });
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> {
         req.response().end("hello from server");
      };
   }

   @Test
   public void startWith0Delay() {
      startWithDifferentDelay("additional", "steady", 0);
   }

   @Test
   public void startWith10Delay() {
      startWithDifferentDelay("additional", "steady", 10);
   }

   public void startWithDifferentDelay(String phase1, String phase2, long delay) {
      BenchmarkBuilder builder = createBuilder(phase1, phase2, delay);
      Benchmark benchmark = builder.build();

      // check startWithDelay is correctly setup
      Phase additionalPhase = benchmark.phases().stream().filter(p -> p.name.equals("additional")).findAny().orElseThrow();
      assertNotNull(additionalPhase.startWithDelay());
      assertEquals("steady", additionalPhase.startWithDelay().phase);
      assertEquals(delay, additionalPhase.startWithDelay().delay);

      StatisticsCollector.StatisticsConsumer statisticsConsumer = (phase, stepId, metric, snapshot, countDown) -> log.debug(
            "Adding stats for {}/{}/{} - #{}: {} requests {} responses", phase, stepId, metric,
            snapshot.sequenceId, snapshot.requestCount, snapshot.responseCount);
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, statisticsConsumer, null, null);
      runner.run();

      // check start time
      long startTimeDiff = runner.instances().get("additional").absoluteStartTime()
            - runner.instances().get("steady").absoluteStartTime();
      assertTrue(startTimeDiff >= delay);
   }

   /**
    * create a builder with two phases where the first one should start with the second one after a provided delay
    */
   private BenchmarkBuilder createBuilder(String firstPhase, String secondPhase, long startWithDelay) {
      // @formatter:off
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("startWithDelay " + new SimpleDateFormat("yy/MM/dd HH:mm:ss").format(new Date()))
            .addPlugin(HttpPluginBuilder::new).http()
            .host("localhost").port(httpServer.actualPort())
            .sharedConnections(50)
            .endHttp().endPlugin()
            .threads(2);

      builder.addPhase(firstPhase).constantRate(200)
            .duration(3000)
            .maxSessions(200 * 15)
            .startWith(new PhaseReferenceDelay(secondPhase, RelativeIteration.NONE, null, startWithDelay))
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                  .path("/")
                  .timeout("60s")
                     .handler()
                     .rawBytes(new TransferSizeRecorder("transfer"))
                     .endHandler()
                  .endStep()
               .endSequence();

      builder.addPhase(secondPhase).constantRate(200)
            .duration(3000)
            .maxSessions(200 * 15)
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                  .path("/")
                  .timeout("60s")
                     .handler()
                     .rawBytes(new TransferSizeRecorder("transfer"))
                     .endHandler()
                  .endStep()
               .endSequence();
      // @formatter:on

      return builder;
   }
}
