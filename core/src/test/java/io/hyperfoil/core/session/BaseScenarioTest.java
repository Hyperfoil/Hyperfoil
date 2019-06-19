package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.HttpBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseScenarioTest {
   protected static final Class<StepCatalog> SC = StepCatalog.class;
   protected final Logger log = LoggerFactory.getLogger(getClass());

   protected Vertx vertx;
   protected Router router;
   protected BenchmarkBuilder benchmarkBuilder;
   protected HttpServer server;

   protected Map<String, List<StatisticsSnapshot>> runScenario() {
      Map<String, List<StatisticsSnapshot>> stats = new HashMap<>();
      StatisticsCollector.StatisticsConsumer statisticsConsumer = (phase, stepId, metric, snapshot, countDown)
            -> stats.computeIfAbsent(metric, n -> new ArrayList<>()).add(snapshot.clone());
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark(), statisticsConsumer, null);
      runner.run();
      return stats;
   }

   protected Benchmark benchmark() {
      return benchmarkBuilder.build();
   }

   @Before
   public void before(TestContext ctx) {
      benchmarkBuilder = BenchmarkBuilder.builder();
      vertx = Vertx.vertx();
      router = Router.router(vertx);
      initRouter();
      server = vertx.createHttpServer().requestHandler(router)
            .listen(0, "localhost", ctx.asyncAssertSuccess(srv -> initWithServer(ctx)));
   }

   protected void initWithServer(TestContext ctx) {
      benchmarkBuilder
            .threads(threads())
            .http().host("localhost").port(server.actualPort());

      initHttp(benchmarkBuilder.http());
   }

   protected void initHttp(HttpBuilder http) {
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
      return benchmarkBuilder.addPhase("test").sequentially(repeats).duration(1).scenario();
   }

   protected ScenarioBuilder parallelScenario(int concurrency) {
      return benchmarkBuilder.addPhase("test").atOnce(concurrency).duration(1).scenario();
   }

   protected int threads() {
      return 3;
   }

   protected StatisticsSnapshot assertSingleItem(List<StatisticsSnapshot> list) {
      assertThat(list).isNotNull();
      assertThat(list.size()).isEqualTo(1);
      return list.iterator().next();
   }
}
