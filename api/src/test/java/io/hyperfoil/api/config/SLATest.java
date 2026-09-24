package io.hyperfoil.api.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class SLATest {

   private static StatisticsSnapshot snapshot() {
      // 90 fast responses (1 ms) and 10 slow responses (100 ms)
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      for (int i = 0; i < 90; ++i) {
         snapshot.histogram.recordValue(TimeUnit.MILLISECONDS.toNanos(1));
      }
      for (int i = 0; i < 10; ++i) {
         snapshot.histogram.recordValue(TimeUnit.MILLISECONDS.toNanos(100));
      }
      snapshot.requestCount = 100;
      snapshot.responseCount = 100;
      return snapshot;
   }

   private static SLA sla(String percentile, String responseTime) {
      SLABuilder<Void> builder = new SLABuilder<>(null);
      builder.limits().accept(percentile, responseTime);
      return builder.build();
   }

   @Test
   public void testPercentileLimitPasses() {
      assertNull(sla("0.9", "10 ms").validate("phase", "metric", snapshot()));
   }

   @Test
   public void testPercentileLimitFails() {
      // 99th percentile is 100 ms; with percentile interpreted as 0.99% this would be 1 ms and pass
      assertNotNull(sla("0.99", "10 ms").validate("phase", "metric", snapshot()));
   }
}
