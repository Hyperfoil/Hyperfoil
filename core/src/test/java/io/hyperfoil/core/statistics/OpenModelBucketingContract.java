package io.hyperfoil.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.NavigableMap;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;
import io.hyperfoil.api.statistics.StatisticsSnapshot;

/**
 * Shared tests for Open Model scenarios.
 * Ensures statistics are saved in the bucket where the request actually fired,
 * even if the system is overloaded and running late.
 */
public interface OpenModelBucketingContract extends StartTimeSourceBucketingContract {

   int TARGET_RATE = 10;
   long DURATION_MS = 3000;
   /** Longer than the 100ms inter-request spacing, so the driver falls behind the rate generator. */
   long STALL_MS = 150;
   String PHASE = "test";
   /** Both plugins default the metric name to the sequence name. */
   String SEQUENCE = "request";

   BenchmarkBuilder benchmarkBuilder();

   /** Configures the protocol and enables compensateInternalLatency. */
   void configureProtocol();

   /** Adds the request step. The metric must use the default SEQUENCE name. */
   void addRequestStep(SequenceBuilder sequence);

   /**
    * Tests that when the driver is overloaded, metrics are saved based on
    * their actual fire time, not their scheduled time.
    */
   @Test
   default void bucketsFollowAchievedFiringWhenTheDriverFallsBehind() {
      configureProtocol();
      BenchmarkBuilder builder = benchmarkBuilder();
      // One executor, so the stall below really does cap achievable throughput; with more, the
      // other event loops absorb it and the driver keeps up with the target rate.
      builder.threads(1);
      // @formatter:off
      ScenarioBuilder scenario = builder.addPhase(PHASE)
            .constantRate(TARGET_RATE) // more than one event loop can serve, so firing falls behind
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(TARGET_RATE * 5)
            .scenario();
      // @formatter:on
      // Stalls the single event loop so subsequent sessions start later than intended.
      scenario.initialSequence("blocker").step(session -> {
         stall();
         return true;
      }).endSequence();
      addRequestStep(scenario.initialSequence(SEQUENCE));

      BucketInvariants collected = BucketInvariants.collect(builder.build());
      collected.dump(LogManager.getLogger(getClass()));
      collected.assertOperationsAreNotSplit(false);

      NavigableMap<Long, StatisticsSnapshot> buckets = collected.buckets(PHASE, SEQUENCE);
      assertThat(buckets.size()).as("the run should cover several buckets").isGreaterThan(1);

      // The rate generator stops issuing intended fire times when the phase duration elapses, so
      // samples can only appear beyond that window if they are filed when they actually fired.
      assertThat(buckets.lastKey() - buckets.firstKey())
            .as("a backed-up driver keeps firing after the phase's scheduled window closed")
            .isGreaterThanOrEqualTo(DURATION_MS);

      // Filed at intended times every bucket would show the full target rate, which an event loop
      // stalled for STALL_MS per request never delivered.
      assertThat(buckets.values().stream().mapToInt(s -> s.requestCount).max().orElseThrow())
            .as("no bucket may claim the target rate the event loop could not achieve")
            .isLessThan(TARGET_RATE);
   }

   private static void stall() {
      try {
         Thread.sleep(STALL_MS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }
}
