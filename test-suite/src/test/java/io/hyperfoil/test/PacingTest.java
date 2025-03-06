package io.hyperfoil.test;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class PacingTest extends BaseHttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route("/test").handler(ctx -> {
         try {
            Thread.sleep(500);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
         ctx.response().end("Hello!");
      });
   }

   @Test
   public void testThinkTimes() {
      scenario().initialSequence("loop")
            .step(SC).loop("counter", 5)
            .steps()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).thinkTime(500, TimeUnit.MILLISECONDS).endStep()
            .end()
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertRequests(stats, 5);
   }

   @Test
   public void testSmallThinkTimes() {
      scenario().initialSequence("loop")
            .step(SC).loop("counter", 5)
            .steps()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).thinkTime(1, TimeUnit.NANOSECONDS).endStep()
            .end()
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertRequests(stats, 5);
   }

   @Test
   public void testCycleTimes() {
      scenario().initialSequence("loop")
            .step(SC).loop("counter", 5)
            .steps()
            // Delaying from now accumulates time skew as it always plans from this timestamp
            .step(SC).scheduleDelay("foo", 1, TimeUnit.SECONDS).fromNow().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).awaitDelay("foo")
            .end()
            .step(SC).log("Final value: ${counter}")
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertRequests(stats, 5);
   }

   @Test
   public void testCycleTimesPrecise() {
      scenario().initialSequence("loop")
            .step(SC).loop("counter", 5)
            .steps()
            // Delaying from last does not accumulate time skew as it bases the delay on previous iteration
            .step(SC).scheduleDelay("foo", 1, TimeUnit.SECONDS).fromLast().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).awaitDelay("foo")
            .end()
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertRequests(stats, 5);
   }

   @Test
   public void testSmallCycleTimesPrecise() {
      scenario().initialSequence("loop")
            .step(SC).loop("counter", 5)
            .steps()
            // Delaying from last does not accumulate time skew as it bases the delay on previous iteration
            .step(SC).scheduleDelay("bar", 500, TimeUnit.NANOSECONDS).fromLast().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).awaitDelay("bar")
            .end()
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertRequests(stats, 5);
   }

   private void assertRequests(Map<String, StatisticsSnapshot> stats, int expected) {
      assertThat(stats.size()).isEqualTo(1);
      StatisticsSnapshot snapshot = stats.values().iterator().next();
      assertThat(snapshot.requestCount).isEqualTo(expected);
      assertThat(snapshot.responseCount).isEqualTo(expected);
   }
}
