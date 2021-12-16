package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.impl.Util;
import io.vertx.core.Vertx;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import io.vertx.ext.unit.TestContext;

public abstract class BaseScenarioTest {
   protected final Logger log = LogManager.getLogger(getClass());

   protected Vertx vertx;
   protected BenchmarkBuilder benchmarkBuilder;

   protected Map<String, StatisticsSnapshot> runScenario() {
      return runScenario(benchmarkBuilder.build());
   }

   protected Map<String, StatisticsSnapshot> runScenario(Benchmark benchmark) {
      TestStatistics statisticsConsumer = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, statisticsConsumer, null, null);
      runner.run();
      return statisticsConsumer.stats();
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
         byte[] bytes = Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
         return benchmark;
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      }
   }

   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(config, TestUtil.benchmarkData(), Collections.emptyMap());
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

   public class TestStatistics implements StatisticsCollector.StatisticsConsumer {
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
