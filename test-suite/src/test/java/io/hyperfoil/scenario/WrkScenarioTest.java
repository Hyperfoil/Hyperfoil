package io.hyperfoil.scenario;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxTestContext;

public class WrkScenarioTest extends BaseBenchmarkTest {

   protected final Logger log = LogManager.getLogger(getClass());

   private int threads = 2;
   private int connections = 10;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      // Create a single-threaded Vertx instance
      VertxOptions options = new VertxOptions().setEventLoopPoolSize(connections);
      this.vertx = Vertx.vertx(options);
      setupHttpServer(ctx, getRequestHandler());
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      Router router = Router.router(vertx);
      router.route("/sleep").handler(ctx -> {
         ctx.vertx().setTimer(500, id -> {
            ctx.response().end("500ms!");
         });
      });
      router.route("/1s").handler(ctx -> {
         ctx.vertx().setTimer(1000, id -> {
            ctx.response().end("1s");
         });
      });
      router.route("/highway").handler(ctx -> {
         ctx.response().end();
      });
      return router;
   }

   @Test
   @Disabled("Issue #626: wrk2 fail with high load - scenario where timeout is 2s")
   // This test can be flaky if Hyperfoil is in a state where calibration phase is able to release the connections back to the pool
   // The current configuration with TRACE logs enabled slow down a lot Hyperfoil
   public void wrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";

      TestStatistics statisticsConsumer = runWrk2Scenario(6, 20, url, 50000, 2);

      Assertions.assertTrue(statisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(statisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   public void wrkRequestsMustBeLowerThanWrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";

      TestStatistics wrkStatisticsConsumer = runWrkScenario(6, 20, url, 2);
      TestStatistics wrk2StatisticsConsumer = runWrk2Scenario(6, 20, url, 20000, 2);

      Assertions.assertTrue(wrkStatisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(wrkStatisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");
      Assertions.assertTrue(wrk2StatisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(wrk2StatisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");

      Assertions.assertTrue(wrk2StatisticsConsumer.stats().get("test").get("request").requestCount <= wrkStatisticsConsumer.stats().get("test").get("request").requestCount);
   }

   @Test
   @Disabled("Issue #638: NPE: Cannot read field session because this.request is null")
   public void wrk2SuperSlowServer() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";
      runWrk2Scenario(6, 20, url, 50000, 2);
   }

   private TestStatistics runWrkScenario(int calibrationDuration, int testDuration, String url, int timeout)
         throws URISyntaxException {
      return runScenario(calibrationDuration, testDuration, url, timeout, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            return catalog.always(connections);
         }
      });
   }

   private TestStatistics runWrk2Scenario(int calibrationDuration, int testDuration, String url, int rate, int timeout)
         throws URISyntaxException {
      return runScenario(calibrationDuration, testDuration, url, timeout, () -> new WrkScenario() {
         @Override
         protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType,
               long durationMs) {
            int durationSeconds = (int) Math.ceil(durationMs / 1000);
            int maxSessions = switch (phaseType) {
               // given that the duration of this phase is 6s seconds
               // there's no point to have more than 6 * rate sessions
               case calibration -> rate * durationSeconds;
               case test -> rate * 15;
            };
            return catalog.constantRate(rate)
                  .variance(false)
                  .maxSessions(maxSessions);
         }
      });
   }

   private TestStatistics runScenario(int calibrationDuration, int testDuration, String url, int timeout,
         Supplier<WrkScenario> fn)
         throws URISyntaxException {
      boolean enableHttp2 = false;
      boolean useHttpCache = false;
      Map<String, String> agent = null;
      String[][] parsedHeaders = null;

      WrkScenario wrkScenario = fn.get();

      BenchmarkBuilder builder = wrkScenario.getBenchmarkBuilder("my-test", url, enableHttp2, connections, useHttpCache,
            threads, agent, calibrationDuration + "s", testDuration + "s", parsedHeaders, timeout + "s");

      TestStatistics statisticsConsumer = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();
      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
      return statisticsConsumer;
   }
}
