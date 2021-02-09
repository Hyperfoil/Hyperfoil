package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.test.TestUtil;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;

public abstract class BaseScenarioTest {
   protected final Logger log = LoggerFactory.getLogger(getClass());

   protected Vertx vertx;
   protected BenchmarkBuilder benchmarkBuilder;

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
      benchmarkBuilder.threads(threads());
      vertx = Vertx.vertx();
   }

   protected Benchmark loadScenario(String name) {
      try {
         InputStream config = getClass().getClassLoader().getResourceAsStream(name);
         Benchmark benchmark = loadBenchmark(config);
         // Serialization here is solely for the purpose of asserting serializability for all the components
         byte[] bytes = io.hyperfoil.util.Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
         return benchmark;
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      }
   }

   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(config, TestUtil.benchmarkData());
   }

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
