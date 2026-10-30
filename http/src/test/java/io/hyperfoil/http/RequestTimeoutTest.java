package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.api.HttpMethod;

public class RequestTimeoutTest extends BaseHttpScenarioTest {
   @Override
   protected void initRouter() {
      router.get("/ok").handler(ctx -> ctx.response().end());
      router.get("/hang").handler(ctx -> {
         // never responds
      });
   }

   @Test
   public void testTimeoutFires() {
      // @formatter:off
      parallelScenario(10)
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/hang")
                  .timeout("200ms")
               .endStep();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot snapshot = stats.get("test");
      assertThat(snapshot.requestTimeouts).isEqualTo(10);
      assertThat(snapshot.responseCount).isEqualTo(0);
   }

   @Test
   public void testCompletedRequestDoesNotTimeOut() {
      // @formatter:off
      parallelScenario(10)
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/ok")
                  .timeout("100ms")
               .endStep()
               .step(SC).thinkTime(300, TimeUnit.MILLISECONDS)
               .endStep();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot snapshot = stats.get("test");
      assertThat(snapshot.responseCount).isEqualTo(10);
      assertThat(snapshot.requestTimeouts).isEqualTo(0);
   }

   @Test
   public void testDifferentTimeouts() {
      // @formatter:off
      parallelScenario(10)
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/ok")
                  .timeout("1s")
               .endStep()
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/hang")
                  .timeout("200ms")
               .endStep();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot snapshot = stats.get("test");
      assertThat(snapshot.responseCount).isEqualTo(10);
      assertThat(snapshot.requestTimeouts).isEqualTo(10);
   }
}
