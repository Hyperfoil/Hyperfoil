package io.hyperfoil.core.session;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Protocol;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;

import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseScenarioTest {
   protected final Logger log = LoggerFactory.getLogger(getClass());

   protected Vertx vertx;
   protected Router router;
   protected BenchmarkBuilder benchmarkBuilder;
   protected HttpServer server;

   protected Map<String, StatisticsSnapshot> runScenario() {
      return runScenario(benchmarkBuilder.build());
   }

   protected Map<String, StatisticsSnapshot> runScenario(Benchmark benchmark) {
      Map<String, StatisticsSnapshot> stats = new HashMap<>();
      StatisticsCollector.StatisticsConsumer statisticsConsumer = (phase, stepId, metric, snapshot, countDown) -> {
         log.debug("Adding stats for {}/{}/{} - #{}: {} requests {} responses", phase, stepId, metric,
               snapshot.sequenceId, snapshot.requestCount, snapshot.responseCount);
         snapshot.addInto(stats.computeIfAbsent(metric, n -> new StatisticsSnapshot()));
      };
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, statisticsConsumer, null);
      runner.run();
      return stats;
   }

   @Before
   public void before(TestContext ctx) {
      benchmarkBuilder = BenchmarkBuilder.builder();
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      HttpServerOptions options = new HttpServerOptions();
      if (useHttps()) {
         options.setSsl(true).setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      }
      server = vertx.createHttpServer(options).requestHandler(router)
            .listen(0, "localhost", ctx.asyncAssertSuccess(srv -> initWithServer(ctx)));
   }

   // override me
   protected boolean useHttps() {
      return false;
   }

   protected void initWithServer(TestContext ctx) {
      benchmarkBuilder
            .threads(threads())
            .http().protocol(useHttps() ? Protocol.HTTPS : Protocol.HTTP).host("localhost").port(server.actualPort());

      initHttp(benchmarkBuilder.http());
   }

   protected void initHttp(HttpBuilder http) {
   }

   protected Benchmark loadScenario(String name) {
      try {
         InputStream config = getClass().getClassLoader().getResourceAsStream(name);
         String configString = Util.toString(config).replaceAll("http://localhost:8080", "http://localhost:" + server.actualPort());
         return BenchmarkParser.instance().buildBenchmark(configString, new LocalBenchmarkData());
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      }
   }

   protected abstract void initRouter();

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }

   protected ScenarioBuilder scenario() {
      return scenario(1);
   }

   protected ScenarioBuilder scenario(int repeats) {
      return benchmarkBuilder.addPhase("test").sequentially(repeats).scenario();
   }

   protected ScenarioBuilder parallelScenario(int concurrency) {
      return benchmarkBuilder.addPhase("test").atOnce(concurrency).scenario();
   }

   protected int threads() {
      return 3;
   }
}
