package io.hyperfoil.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.util.CountDown;

/**
 * Collects published statistics per sampling bucket - as opposed to
 * {@code BaseScenarioTest.TestStatistics}, which aggregates every bucket of a metric into one
 * snapshot and so cannot see where a sample was filed.
 */
public class BucketInvariants implements StatisticsCollector.StatisticsConsumer {

   // "<phase>/<metric>" -> bucket start timestamp -> merged snapshot
   private final Map<String, NavigableMap<Long, StatisticsSnapshot>> series = new TreeMap<>();

   /** Runs the benchmark locally and returns the per-bucket statistics it published. */
   public static BucketInvariants collect(Benchmark benchmark) {
      BucketInvariants collected = new BucketInvariants();
      new LocalSimulationRunner(benchmark, collected, null, null).run();
      return collected;
   }

   @Override
   public void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown) {
      // The snapshot is reset once we return, and the same bucket may be published more than once
      // (one visit per executor, or a later visit picking up late arrivals), so merge into a copy.
      series.computeIfAbsent(key(phase.name, metric), k -> new TreeMap<>())
            .computeIfAbsent(snapshot.histogram.getStartTimeStamp(), k -> new StatisticsSnapshot())
            .add(snapshot);
   }

   public static String key(String phase, String metric) {
      return phase + "/" + metric;
   }

   public Map<String, NavigableMap<Long, StatisticsSnapshot>> series() {
      return series;
   }

   /** Buckets of one series, keyed by bucket start timestamp, in chronological order. */
   public NavigableMap<Long, StatisticsSnapshot> buckets(String phase, String metric) {
      NavigableMap<Long, StatisticsSnapshot> buckets = series.get(key(phase, metric));
      assertThat(buckets).as("no statistics published for %s; got %s", key(phase, metric), series.keySet()).isNotNull();
      return buckets;
   }

   /** Earliest bucket across all series, so offsets can be reported relative to the run. */
   public long firstBucketTimestamp() {
      return series.values().stream().flatMap(b -> b.keySet().stream()).mapToLong(Long::longValue).min().orElseThrow();
   }

   /**
    * Verifies that all statistics (request count, response count, blocked time)
    * for a single operation land in the same bucket.
    * Blocked time must never be alone in a bucket.
    *
    * @param blockedTime true if this test is expected to have recorded blocked time.
    */
   public void assertOperationsAreNotSplit(boolean blockedTime) {
      assertThat(series).as("benchmark published no statistics at all").isNotEmpty();
      series.forEach((name, buckets) -> buckets.forEach((bucket, stats) -> {
         assertThat(stats.responseCount + stats.errors())
               .as("%s bucket %d: every counted request must have its outcome in the same bucket (%s)",
                     name, bucket, stats)
               .isEqualTo(stats.requestCount);
         if (stats.blockedTime > 0) {
            assertThat(stats.requestCount)
                  .as("%s bucket %d: blocked time must share a bucket with the request it blocked (%s)",
                        name, bucket, stats)
                  .isGreaterThan(0);
         }
      }));
      if (blockedTime) {
         assertThat(totalBlockedTime())
               .as("no bucket recorded blocked time, so the co-location check above asserted "
                     + "nothing; the scenario is meant to make a request wait")
               .isGreaterThan(0);
      }
   }

   /** Returns the total blocked time across all buckets, in nanoseconds. */
   public long totalBlockedTime() {
      return series.values().stream().flatMap(b -> b.values().stream()).mapToLong(s -> s.blockedTime).sum();
   }

   /** Logs one row per bucket; offsets are relative to the earliest bucket of the run. */
   public void dump(Logger log) {
      long base = firstBucketTimestamp();
      series.forEach((name, buckets) -> {
         log.info("--- {} ---", name);
         log.info("bucket | requests | responses | errors | blocked | mean latency");
         buckets.forEach((bucket, stats) -> log.info(String.format("  +%2ds  |   %3d    |    %3d    |  %3d   | %5d ms | %5d ms",
               (bucket - base) / 1000, stats.requestCount, stats.responseCount, stats.errors(),
               stats.blockedTime / 1_000_000, (long) (stats.histogram.getMean() / 1_000_000))));
      });
   }
}
