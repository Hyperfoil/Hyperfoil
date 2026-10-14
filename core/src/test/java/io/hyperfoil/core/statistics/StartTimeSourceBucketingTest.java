package io.hyperfoil.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.NavigableMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.core.steps.StopwatchBeginStep;

/**
 * Tests that statistics are bucketed correctly for core components.
 */
public class StartTimeSourceBucketingTest extends BaseScenarioTest implements StartTimeSourceBucketingContract {

   private static final long SAMPLING_PERIOD_MS = 1000;
   // Longer than the sampling period, so begin and end fall in different buckets.
   private static final long BLOCK_MS = 1500;

   /**
    * Tests that a stopwatch block is saved in the bucket where it started,
    * even if it finishes in a later time bucket.
    */
   @Test
   public void stopwatchIsFiledWhereTheBlockBegan() {
      ScenarioBuilder scenario = scenario();
      // The marker runs concurrently with the timed block and finishes immediately, so its bucket
      // is the one both blocks started in.
      timedBlock(scenario, "marker", 1);
      timedBlock(scenario, "timed", BLOCK_MS);

      BucketInvariants collected = BucketInvariants.collect(benchmarkBuilder.build());
      collected.dump(log);
      collected.assertOperationsAreNotSplit(false);

      NavigableMap<Long, StatisticsSnapshot> timed = collected.buckets("test", "timed");
      assertThat(timed).as("the measured block must not be spread over several buckets").hasSize(1);

      StatisticsSnapshot stats = timed.firstEntry().getValue();
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(1);
      // Histogram precision rounds the recorded value down slightly, so compare against the
      // sampling period rather than BLOCK_MS: outliving one period is what spans a boundary.
      assertThat((long) (stats.histogram.getMean() / 1_000_000))
            .as("the block really did span a bucket boundary")
            .isGreaterThan(SAMPLING_PERIOD_MS);

      // If the fired timestamp were taken at the end of the block instead of the beginning, the
      // sample would land a bucket later than the marker that started alongside it.
      assertThat(timed.firstKey())
            .as("block began alongside the marker, so it belongs in the marker's bucket")
            .isEqualTo(collected.buckets("test", "marker").firstKey());
   }

   /** A stopwatch block of the given duration; the metric takes the name of the sequence. */
   private void timedBlock(ScenarioBuilder scenario, String sequence, long blockMs) {
      StopwatchBeginStep.Builder stopwatch = scenario.initialSequence(sequence).step(StepCatalog.SC).stopwatch();
      stopwatch.step(StepCatalog.SC).thinkTime(blockMs, TimeUnit.MILLISECONDS).endStep();
   }
}
