package io.hyperfoil.core.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.hyperfoil.core.session.SessionFactory;

public class StopWatchCompensateInternalLatencyTest extends BaseScenarioTest {

   private static final int RATE = 10;
   private static final long DURATION_MS = 3000;

   // Sleep longer than the inter-request spacing (100ms) to block the event loop.
   private static final long SLEEP_MS = 150;

   private long runWithCompensation(boolean enabled) {
      // @formatter:off
      benchmarkBuilder.addPhase("test")
            .constantRate(RATE)
            .variance(false)
            .duration(DURATION_MS)
            .maxSessions(RATE * 5)
            .scenario()

            // 1) The Blocker Sequence
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
            .initialSequence("request")
            // Use an anonymous BaseStepBuilder to defer instantiation until the build phase
            // when the Locator context is fully initialized.
            .stepBuilder(new BaseStepBuilder() {
               @Override
               public java.util.List<io.hyperfoil.api.config.Step> build() {
                  Object key = new Object();
                  return java.util.Arrays.asList(
                        new StopwatchBeginStep(SessionFactory.objectAccess(key), enabled),
                        new StopwatchEndStep(SessionFactory.readAccess(key), "request")
                  );
               }
            });
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

      assertThat(maxResponseTimeNanos)
            .describedAs("With compensation, the max response time should include the delayed event loop wait")
            .isGreaterThanOrEqualTo(SLEEP_MS * 1_000_000L);
   }

   @Test
   public void testDisabledStartTimestampsInflatedByDelay() {
      long maxResponseTimeNanos = runWithCompensation(false);

      assertThat(maxResponseTimeNanos)
            .describedAs("Without compensation, the max response time should purely reflect server execution")
            .isLessThan(SLEEP_MS * 1_000_000L);
   }
}
