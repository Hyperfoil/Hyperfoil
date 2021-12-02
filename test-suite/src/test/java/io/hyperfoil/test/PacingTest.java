package io.hyperfoil.test;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
@Ignore // Ignoring in default run since it takes 3x 5 seconds
public class PacingTest extends HttpScenarioTest {
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

   private void assertRequests(Map<String, StatisticsSnapshot> stats, int expected) {
      assertThat(stats.size()).isEqualTo(1);
      StatisticsSnapshot snapshot = stats.values().iterator().next();
      assertThat(snapshot.requestCount).isEqualTo(expected);
      assertThat(snapshot.responseCount).isEqualTo(expected);
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
            .step(SC).log().message("Final value: {}").addVar("counter", null).end()
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

}
