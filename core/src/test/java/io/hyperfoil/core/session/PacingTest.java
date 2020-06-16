package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
@Ignore // Ignoring in default run since it takes 3x 5 seconds
public class PacingTest extends BaseScenarioTest {
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
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).thinkTime(500, TimeUnit.MILLISECONDS).endStep()
            .step(SC).loop("counter", 5, "loop")
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      // The loop step actually schedules 5 MORE repetitions
      assertRequests(stats, 6);
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
            // Delaying from now accumulates time skew as it always plans from this timestamp
            .step(SC).scheduleDelay("foo", 1, TimeUnit.SECONDS).fromNow().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).awaitDelay("foo")
            .step(SC).loop("counter", 5, "loop")
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      // The loop step actually schedules 5 MORE repetitions
      assertRequests(stats, 6);
   }

   @Test
   public void testCycleTimesPrecise() {
      scenario().initialSequence("loop")
            // Delaying from last does not accumulate time skew as it bases the delay on previous iteration
            .step(SC).scheduleDelay("foo", 1, TimeUnit.SECONDS).fromLast().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/test").endStep()
            .step(SC).clearHttpCache()
            .step(SC).awaitDelay("foo")
            .step(SC).loop("counter", 5, "loop")
            .endSequence();

      Map<String, StatisticsSnapshot> stats = runScenario();
      // The loop step actually schedules 5 MORE repetitions
      assertRequests(stats, 6);
   }

}
