package io.sailrocket.core.session;

import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.builders.ScenarioBuilder;
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
      ScenarioBuilder scenarioBuilder = scenarioBuilder()
            .initialSequence("loop")
               .step().httpRequest(HttpMethod.GET).path("/test").endStep()
               .step().awaitAllResponses()
               .step().thinkTime(500, TimeUnit.MILLISECONDS).endStep()
               .step().loop("counter", 5, "loop")
            .endSequence();

      runScenario(scenarioBuilder.build(), 1);
   }

   @Test
   public void testCycleTimes() {
      ScenarioBuilder scenarioBuilder = scenarioBuilder()
            .initialSequence("loop")
               // Delaying from now accumulates time skew as it always plans from this timestamp
               .step().scheduleDelay("foo", 1, TimeUnit.SECONDS).fromNow().endStep()
               .step().httpRequest(HttpMethod.GET).path("/test").endStep()
               .step().awaitAllResponses()
               .step().awaitDelay("foo")
               .step().loop("counter", 5, "loop")
            .endSequence();

      runScenario(scenarioBuilder.build(), 1);
   }

   @Test
   public void testCycleTimesPrecise() {
      ScenarioBuilder scenarioBuilder = scenarioBuilder()
            .initialSequence("loop")
               // Delaying from last does not accumulate time skew as it bases the delay on previous iteration
               .step().scheduleDelay("foo", 1, TimeUnit.SECONDS).fromLast().endStep()
               .step().httpRequest(HttpMethod.GET).path("/test").endStep()
               .step().awaitAllResponses()
               .step().awaitDelay("foo")
               .step().loop("counter", 5, "loop")
            .endSequence();

      runScenario(scenarioBuilder.build(), 1);
   }

}
