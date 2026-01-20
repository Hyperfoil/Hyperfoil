package io.hyperfoil.core.session;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.util.CountDown;

public abstract class BaseScenarioTest extends BaseBenchmarkParserTest {
   protected final Logger log = LogManager.getLogger(getClass());

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

   @BeforeEach
   public void before() {
      benchmarkBuilder = BenchmarkBuilder.builder();
      benchmarkBuilder.threads(threads());
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

   public static class TestStatistics implements StatisticsCollector.StatisticsConsumer {
      private final Logger log = LogManager.getLogger(getClass());

      private final Map<String, Map<String, StatisticsSnapshot>> phaseStats = new HashMap<>();

      @Override
      public void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown) {
         log.debug("Adding stats for {}/{}/{} - #{}: {} requests {} responses", phase.name, stepId, metric,
               snapshot.sequenceId, snapshot.requestCount, snapshot.responseCount);
         Map<String, StatisticsSnapshot> stats = phaseStats.get(phase.name);
         if (stats == null) {
            stats = new HashMap<>();
            phaseStats.put(phase.name, stats);
         }
         StatisticsSnapshot metricValue = stats.get(metric);
         if (metricValue == null) {
            metricValue = new StatisticsSnapshot();
            stats.put(metric, metricValue);
         }
         metricValue.add(snapshot);
      }

      /**
       * This is for compatibility. Prefer always using the phaseStats method
       *
       * @return stats
       */
      @Deprecated
      public Map<String, StatisticsSnapshot> stats() {
         if (this.phaseStats.isEmpty()) {
            return Map.of();
         } else {
            return this.phaseStats.values().iterator().next();
         }
      }

      public Map<String, Map<String, StatisticsSnapshot>> phaseStats() {
         return phaseStats;
      }
   }
}
