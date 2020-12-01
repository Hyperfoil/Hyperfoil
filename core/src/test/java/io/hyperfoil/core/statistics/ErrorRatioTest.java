package io.hyperfoil.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ErrorRatioTest extends BaseScenarioTest {

   @Override
   protected void initRouter() {
      router.get("/get200").handler(ctx -> ctx.response().setStatusCode(200).end());
      router.get("/get400").handler(ctx -> ctx.response().setStatusCode(400).end());
      router.get("/close").handler(ctx -> ctx.response().close());
   }

   @Test
   public void test400(TestContext ctx) {
      scenario().initialSequence("400")
            .step(StepCatalog.SC).httpRequest(HttpMethod.GET).path("/get400")
            .handler().onCompletion(validateConnection(ctx)).endHandler()
            .endStep();
      StatisticsSnapshot stats = runScenario().get("400");
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(stats.status_4xx).isEqualTo(1);
      assertThat(stats.resetCount).isEqualTo(0);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.cacheHits).isEqualTo(0);
      assertThat(stats.connectFailureCount).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(0);
   }

   protected Action validateConnection(TestContext ctx) {
      return session -> { };
   }

   @Test
   public void testClose() {
      scenario().initialSequence("close")
            .step(StepCatalog.SC).httpRequest(HttpMethod.GET).path("/close").endStep();
      StatisticsSnapshot stats = runScenario().get("close");
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(0);
      assertThat(stats.resetCount).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.cacheHits).isEqualTo(0);
      assertThat(stats.connectFailureCount).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(1);
   }

   @Test
   public void testThrowInBodyHandler() {
      scenario().initialSequence("throw")
            .step(StepCatalog.SC).httpRequest(HttpMethod.GET).path("/get200")
            .handler().body(fragmented -> (session, data, offset, length, isLastPart) -> {
         throw new RuntimeException("Induced failure");
      }).endHandler().endStep();

      StatisticsSnapshot stats = runScenario().get("throw");
      assertThat(stats.requestCount).isEqualTo(1);
      // handleEnd was not invoked and handleThrowable cannot record response
      // because it does not know if the complete physical response was received.
      assertThat(stats.responseCount).isEqualTo(0);
      assertThat(stats.status_2xx).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.cacheHits).isEqualTo(0);
      assertThat(stats.resetCount).isEqualTo(0);
      assertThat(stats.internalErrors).isEqualTo(1);
      assertThat(stats.errors()).isEqualTo(1);
   }

   @Test
   public void testThrowInCompletionHandler() {
      scenario().initialSequence("throw")
            .step(StepCatalog.SC).httpRequest(HttpMethod.GET).path("/get200")
            .handler().onCompletion(() -> session -> {
         throw new RuntimeException("Induced failure");
      }).endHandler().endStep();

      StatisticsSnapshot stats = runScenario().get("throw");
      assertThat(stats.requestCount).isEqualTo(1);
      // contrary to testThrowInBodyHandler the response is already recorded before the completion handlers run
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(stats.status_2xx).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.cacheHits).isEqualTo(0);
      assertThat(stats.resetCount).isEqualTo(0);
      assertThat(stats.internalErrors).isEqualTo(1);
      assertThat(stats.errors()).isEqualTo(1);
   }
}
