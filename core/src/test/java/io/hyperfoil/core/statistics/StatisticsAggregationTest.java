package io.hyperfoil.core.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.Statistics;

public class StatisticsAggregationTest {

   private static final Logger log = LogManager.getLogger(StatisticsAggregationTest.class);

   private final long baseTime = 1710774000000L;
   private final long millis = 1000L;

   @Test
   public void testStatisticsTimeDistributionWithoutSequentialData() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      for (int i = 1; i < 100; i++) {
         if (i % 2 == 0) {
            requestData.put(baseTime + (i * millis), i);
         }
      }
      runExperiment(requestData);
   }

   @Test
   public void testStatisticsTimeDistributionWithSequentialData() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      for (int i = 1; i < 100; i++) {
         requestData.put(baseTime + (i * millis), i);
      }
      runExperiment(requestData);
   }

   @Test
   public void testEmptyData() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      // No data added - testing empty case
      runExperiment(requestData);
   }

   @Test
   public void testSingleTimestamp() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      requestData.put(baseTime + millis, 42);
      runExperiment(requestData);
   }

   @Test
   public void testVeryLargeGapsBetweenTimestamps() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      // Add timestamps with very large gaps (hours apart)
      requestData.put(baseTime + (1 * millis), 10);
      requestData.put(baseTime + (3600 * millis), 20); // 1 hour gap
      requestData.put(baseTime + (7200 * millis), 30); // Another 1 hour gap
      requestData.put(baseTime + (10800 * millis), 40); // Another 1 hour gap
      runExperiment(requestData);
   }

   @Test
   public void testTimestampsAtArrayBoundaries() {
      Map<Long, Integer> requestData = new LinkedHashMap<>();
      // Test at initial array size boundary (16)
      requestData.put(baseTime + (15 * millis), 15);
      requestData.put(baseTime + (16 * millis), 16);
      requestData.put(baseTime + (17 * millis), 17);

      // Test at doubled array size boundary (32)
      requestData.put(baseTime + (31 * millis), 31);
      requestData.put(baseTime + (32 * millis), 32);
      requestData.put(baseTime + (33 * millis), 33);

      // Test at next boundary (64)
      requestData.put(baseTime + (63 * millis), 63);
      requestData.put(baseTime + (64 * millis), 64);
      requestData.put(baseTime + (65 * millis), 65);

      runExperiment(requestData);
   }

   @Test
   public void testSingleEntryBeyondInitialArraySize() {
      long timestamp = baseTime + (20 * millis);
      Statistics stats = new Statistics(baseTime);
      stats.recordResponse(timestamp, 100_000);
      stats.end(timestamp + millis);

      Map<Long, Integer> statsPerTimestamp = new HashMap<>();
      stats.visitSnapshots(snapshot -> {
         if (snapshot.responseCount > 0) {
            statsPerTimestamp.put(snapshot.histogram.getStartTimeStamp(), snapshot.responseCount);
         }
      });

      assertEquals(1, statsPerTimestamp.size(), "Expected exactly one time slot with data");
      assertEquals(1, statsPerTimestamp.getOrDefault(timestamp, 0),
            "Response should be at index 20 (timestamp " + timestamp + "), not clamped to index 15");
   }

   @Test
   public void testMultipleVisitSnapshotsWithOngoingRecording() {
      Statistics stats = new Statistics(baseTime);

      for (int i = 1; i <= 5; i++) {
         stats.recordResponse(baseTime + (i * millis), 100_000);
      }

      // Last index withheld (phase not ended), so indices 1-4 published
      Map<Long, Integer> phase1 = new HashMap<>();
      stats.visitSnapshots(snapshot -> {
         if (snapshot.responseCount > 0) {
            phase1.put(snapshot.histogram.getStartTimeStamp(), snapshot.responseCount);
         }
      });
      assertEquals(4, phase1.values().stream().mapToInt(Integer::intValue).sum(),
            "Phase 1: indices 1-4 published (index 5 withheld)");

      // Record beyond initial array size, forcing resize
      for (int i = 20; i <= 25; i++) {
         stats.recordResponse(baseTime + (i * millis), 100_000);
      }

      // Inactive resized before swap; indices 20-24 published (25 withheld)
      Map<Long, Integer> phase2 = new HashMap<>();
      stats.visitSnapshots(snapshot -> {
         if (snapshot.responseCount > 0) {
            phase2.put(snapshot.histogram.getStartTimeStamp(), snapshot.responseCount);
         }
      });
      assertEquals(5, phase2.values().stream().mapToInt(Integer::intValue).sum());
      for (int i = 20; i <= 24; i++) {
         long ts = baseTime + (i * millis);
         assertEquals(1, phase2.getOrDefault(ts, 0));
      }

      // End: both arrays published, withheld indices 5 and 25 collected
      stats.end(baseTime + (30 * millis));

      Map<Long, Integer> phase3 = new HashMap<>();
      stats.visitSnapshots(snapshot -> {
         if (snapshot.responseCount > 0) {
            phase3.put(snapshot.histogram.getStartTimeStamp(), snapshot.responseCount);
         }
      });
      long ts5 = baseTime + (5 * millis);
      long ts25 = baseTime + (25 * millis);
      assertEquals(1, phase3.getOrDefault(ts5, 0));
      assertEquals(1, phase3.getOrDefault(ts25, 0));
   }

   private void runExperiment(Map<Long, Integer> requestData) {
      Statistics stats = new Statistics(baseTime);
      long end = 0;
      int totalRecorded = 0;
      for (Map.Entry<Long, Integer> entry : requestData.entrySet()) {
         long timestamp = entry.getKey();
         int count = entry.getValue();
         for (int i = 0; i < count; i++) {
            stats.recordResponse(timestamp, 100_000);
         }
         totalRecorded += count;
         end = timestamp + millis;
      }
      stats.end(end);

      Map<Long, Integer> statsPerTimestamp = new HashMap<>();
      stats.visitSnapshots(snapshot -> {
         if (snapshot.responseCount > 0) {
            statsPerTimestamp.put(snapshot.histogram.getStartTimeStamp(), snapshot.responseCount);
         }
      });

      int totalCollected = statsPerTimestamp.values().stream().mapToInt(Integer::intValue).sum();
      assertEquals(totalRecorded, totalCollected);

      log.info("Per-timestamp comparison:");
      log.info("Offset(s) | Value | Statistics | Match");
      log.info("----------|-----|------------|------");

      for (Map.Entry<Long, Integer> entry : requestData.entrySet()) {
         long timestamp = entry.getKey();
         int value = entry.getValue();
         int stat = statsPerTimestamp.getOrDefault(timestamp, 0);
         boolean match = value == stat;
         String matchStr = match ? "✓" : "✗";
         int offsetSeconds = (int) ((timestamp - baseTime) / millis);
         log.info(String.format("   +%2ds    | %3d |    %3d     |  %s", offsetSeconds, value, stat, matchStr));
      }

      for (Map.Entry<Long, Integer> entry : requestData.entrySet()) {
         long timestamp = entry.getKey();
         int value = entry.getValue();
         int stat = statsPerTimestamp.getOrDefault(timestamp, 0);
         assertEquals(value, stat, "Timestamp " + timestamp + ": value=" + value + " but Statistics=" + stat);
      }
   }
}
