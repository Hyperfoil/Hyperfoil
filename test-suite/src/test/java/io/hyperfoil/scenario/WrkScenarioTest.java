package io.hyperfoil.scenario;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.WrkScenario;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;

public class WrkScenarioTest extends BaseBenchmarkTest {

   protected final Logger log = LogManager.getLogger(getClass());

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      Router router = Router.router(vertx);
      router.route("/sleep").handler(ctx -> {
         try {
            Thread.sleep(500);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         ctx.response().end("Hello!");
      });
      router.route("/highway").handler(ctx -> {
         ctx.response().end();
      });
      return router;
   }

   @Test
   @Disabled("Issue #626: wrk2 fail with high load")
   public void wrk2Test() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/highway";
      runScenario(url);
   }

   @Test
   @Disabled("Issue #638: NPE: Cannot read field session because this.request is null")
   public void wrk2SuperSlowServer() throws URISyntaxException {
      String url = "localhost:" + httpServer.actualPort() + "/sleep";
      runScenario(url);
   }

   private void runScenario(String url) throws URISyntaxException {
      boolean enableHttp2 = false;
      int connections = 10;
      boolean useHttpCache = false;
      int threads = 2;
      Map<String, String> agent = null;
      String duration = "10s";
      String[][] parsedHeaders = null;
      String timeout = "2s";
      int rate = 100000;

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
            threads, agent, duration, parsedHeaders, timeout);

      TestStatistics statisticsConsumer = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(builder.build(), statisticsConsumer, null, null);
      runner.setEnableWatchdog(false);
      long start = System.currentTimeMillis();
      runner.run();
      long end = System.currentTimeMillis();

      log.info("Test duration: " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");

      Assertions.assertTrue(statisticsConsumer.stats().containsKey("calibration"));
      Assertions.assertTrue(statisticsConsumer.stats().containsKey("test"));
   }
}
