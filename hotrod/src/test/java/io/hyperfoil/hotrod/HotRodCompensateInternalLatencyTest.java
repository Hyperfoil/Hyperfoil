package io.hyperfoil.hotrod;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.hotrod.config.HotRodPluginBuilder;
import io.hyperfoil.hotrod.steps.HotRodRequestBuilder;

public class HotRodCompensateInternalLatencyTest extends BaseHotRodTest {

   private static final int RATE = 10; // 10 req/s
   private static final long DURATION_MS = 3000;

   // Sleep longer than the inter-request spacing (100ms) to block the event loop,
   // delaying the execution of the subsequent sequences.
   private static final long SLEEP_MS = 150;

   @Override
   protected void createCache(EmbeddedCacheManager em) {
      em.administration().withFlags(org.infinispan.commons.api.CacheContainerAdmin.AdminFlag.VOLATILE)
            .getOrCreateCache("default", new ConfigurationBuilder().build());
   }

   private long runWithCompensation(boolean enabled) {
      HotRodPluginBuilder hotRodPlugin = benchmarkBuilder.addPlugin(HotRodPluginBuilder::new);
      hotRodPlugin.addCluster()
            .uri("hotrod://127.0.0.1:" + hotrodServers[0].getPort())
            .addCache("default");
      hotRodPlugin.ergonomics().compensateInternalLatency(enabled);

      // @formatter:off
      benchmarkBuilder.addPhase("test")
            .constantRate(RATE)
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(RATE * 5)
            .scenario()

            // The Blocker Sequence
            .initialSequence("blocker")
            .step(session -> {
               try {
                  Thread.sleep(SLEEP_MS);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
               return true;
            })
            .endSequence()

            // The HotRod Request Sequence
            .initialSequence("request")
            .stepBuilder(new HotRodRequestBuilder()
                  .put("default")
                  .key("test-key")
                  .value("test-value"));
      // @formatter:on

      Map<String, StatisticsSnapshot> stats = runScenario();

      StatisticsSnapshot snapshot = stats.get("request");
      if (snapshot == null) {
         throw new IllegalStateException("No statistics found for the 'request' sequence");
      }

      return snapshot.histogram.getMaxValue();
   }

   @Test
   public void testEnabledStartTimestampsMatchIntendedFireTimes() {
      long maxResponseTimeNanos = runWithCompensation(true);

      // With compensation, the response time is measured against the scheduled session start.
      // Because the thread slept for SLEEP_MS, the completion happens after the sleep,
      // resulting in a recorded response time that includes the wait time.
      assertThat(maxResponseTimeNanos)
            .describedAs("With compensation, the max response time should include the delayed event loop wait")
            .isGreaterThanOrEqualTo(SLEEP_MS * 1_000_000L);
   }

   @Test
   public void testDisabledStartTimestampsInflatedByDelay() {
      long maxResponseTimeNanos = runWithCompensation(false);

      // Without compensation, the response time is measured from when the HotRodRequestStep
      // actually started executing (which occurs *after* the sleep).
      // Because the local embedded server is extremely fast, this will be only a few milliseconds.
      assertThat(maxResponseTimeNanos)
            .describedAs("Without compensation, the max response time should purely reflect server execution")
            .isLessThan(SLEEP_MS * 1_000_000L);
   }
}
