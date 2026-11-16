package io.hyperfoil.core.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.core.builders.StepCatalog;

public class SubMillisecondDetectorTest {

   @Test
   public void testConstantRateBelowThreshold() {
      assertFalse(SubMillisecondDetector.requiresSubMillisecondPrecision(constantRateBenchmark(500), 0));
   }

   @Test
   public void testConstantRateAtThresholdBoundary() {
      // exactly 1000 users/sec yields a 1ms period, so it must not be flagged
      assertFalse(SubMillisecondDetector.requiresSubMillisecondPrecision(constantRateBenchmark(1000), 0));
   }

   @Test
   public void testConstantRateAboveThreshold() {
      assertTrue(SubMillisecondDetector.requiresSubMillisecondPrecision(constantRateBenchmark(2000), 0));
   }

   @Test
   public void testRampRateBelowThreshold() {
      assertFalse(SubMillisecondDetector.requiresSubMillisecondPrecision(rampRateBenchmark(10, 500), 0));
   }

   @Test
   public void testRampRateTargetAboveThreshold() {
      assertTrue(SubMillisecondDetector.requiresSubMillisecondPrecision(rampRateBenchmark(10, 2000), 0));
   }

   @Test
   public void testRampRateInitialAboveThreshold() {
      assertTrue(SubMillisecondDetector.requiresSubMillisecondPrecision(rampRateBenchmark(2000, 10), 0));
   }

   @Test
   public void testPerAgentSlicingWithUnevenThreads() {
      // 2 agents, threads 1 and 3 (4 total): a rate of 2000/sec slices to 500 (agent0) and 1500 (agent1)
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("test")
            .addAgent("agent0", null, java.util.Map.of("threads", "1"))
            .addAgent("agent1", null, java.util.Map.of("threads", "3"));
      addConstantRatePhase(builder, 2000);
      Benchmark benchmark = builder.build();

      // agent0 (agentId=0, threads=1): 2000 * 1 / 4 = 500 → below the 1000 threshold
      assertFalse(SubMillisecondDetector.requiresSubMillisecondPrecision(benchmark, 0));
      // agent1 (agentId=1, threads=3): 2000 * 3 / 4 = 1500 → above the 1000 threshold
      assertTrue(SubMillisecondDetector.requiresSubMillisecondPrecision(benchmark, 1));
   }

   private static Benchmark constantRateBenchmark(int rate) {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("test");
      addConstantRatePhase(builder, rate);
      return builder.build();
   }

   private static void addConstantRatePhase(BenchmarkBuilder builder, int rate) {
      PhaseBuilder.Catalog catalog = builder.addPhase("testPhase");
      catalog.constantRate(rate).duration(1000)
            .scenario()
            .initialSequence("sequence")
            .step(StepCatalog.SC).thinkTime(1, TimeUnit.MILLISECONDS)
            .endStep()
            .endSequence();
   }

   private static Benchmark rampRateBenchmark(int initialRate, int targetRate) {
      BenchmarkBuilder builder = BenchmarkBuilder.builder().name("test");
      PhaseBuilder.Catalog catalog = builder.addPhase("testPhase");
      catalog.rampRate(initialRate, targetRate).duration(1000)
            .scenario()
            .initialSequence("sequence")
            .step(StepCatalog.SC).thinkTime(1, TimeUnit.MILLISECONDS)
            .endStep()
            .endSequence();
      return builder.build();
   }
}
