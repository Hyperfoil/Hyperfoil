package io.hyperfoil.scenario;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
   private int duration = 20;
   private String timeout = "2s";
   private int rate = 50000;

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
   @Disabled("Issue #626: wrk2 fail with high load")
   // This test can be flaky if Hyperfoil is in a state where calibration phase is able to release the connections back to the pool
   // The current configuration with TRACE logs enabled slow down a lot Hyperfoil
   public void wrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";

      TestStatistics statisticsConsumer = runScenario(url);

      Assertions.assertTrue(statisticsConsumer.stats().containsKey("calibration"),
            "Stats must have values for the 'calibration' phase");
      Assertions.assertTrue(statisticsConsumer.stats().containsKey("test"), "Stats must have values for the 'test' phase");
   }

   @Test
   @Disabled("Issue #638: NPE: Cannot read field session because this.request is null")
   public void wrk2SuperSlowServer() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";
      runScenario(url);
   }

   private TestStatistics runScenario(String url) throws URISyntaxException {
      boolean enableHttp2 = false;
      boolean useHttpCache = false;
      Map<String, String> agent = null;
      String[][] parsedHeaders = null;

      WrkScenario wrkScenario = new WrkScenario() {
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
      };

      BenchmarkBuilder builder = wrkScenario.getBenchmarkBuilder("my-test", url, enableHttp2, connections, useHttpCache,
            threads, agent, duration + "s", parsedHeaders, timeout);

      TestStatistics statisticsConsumer = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();
      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
      return statisticsConsumer;
   }
}
