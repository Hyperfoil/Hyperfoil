package io.hyperfoil.http.statistics;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.handlers.CheckProcessor;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class ErrorRatioTest extends BaseHttpScenarioTest {

   @Override
   protected void initRouter() {
      router.get("/get200").handler(ctx -> ctx.response().setStatusCode(200).end());
      router.get("/get400").handler(ctx -> ctx.response().setStatusCode(400).end());
      router.get("/close").handler(ctx -> ctx.response().reset());
      router.get("/malformed-json").handler(ctx -> ctx.response().setChunked(true).write("{\"value\":")
            .onComplete(ignored -> ctx.response().end("false")));
      router.get("/valid-equal").handler(ctx -> ctx.response().setChunked(true).write("response ")
            .onComplete(ignored -> ctx.response().end("body")));
      router.get("/valid-regex").handler(ctx -> ctx.response().setChunked(true).write("prefix-")
            .onComplete(ignored -> ctx.response().end("middle-suffix")));
      router.get("/valid-json").handler(ctx -> ctx.response().setChunked(true).write("{\"values\":[1.0,true],")
            .onComplete(ignored -> ctx.response().end("\"message\":\"ok\"}")));
   }

   @Test
   public void test400(VertxTestContext ctx) {
      scenario().initialSequence("400")
            .step(SC).httpRequest(HttpMethod.GET).path("/get400")
            .handler().onCompletion(validateConnection(ctx)).endHandler()
            .endStep();
      StatisticsSnapshot stats = runScenario().get("400");
      HttpStats http = HttpStats.get(stats);
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(http.status_4xx).isEqualTo(1);
      assertThat(stats.connectionErrors).isEqualTo(0);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(http.cacheHits).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(0);
   }

   protected Action validateConnection(VertxTestContext ctx) {
      return session -> ctx.completeNow();
   }

   @Test
   public void testClose() {
      scenario().initialSequence("close")
            .step(SC).httpRequest(HttpMethod.GET).path("/close").endStep();
      StatisticsSnapshot stats = runScenario().get("close");
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(0);
      assertThat(stats.connectionErrors).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(HttpStats.get(stats).cacheHits).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(1);
   }

   @Test
   public void testThrowInBodyHandler() {
      scenario().initialSequence("throw")
            .step(SC).httpRequest(HttpMethod.GET).path("/get200")
            .handler().body(fragmented -> (session, data, offset, length, isLastPart) -> {
               throw new RuntimeException("Induced failure");
            }).endHandler().endStep();

      StatisticsSnapshot stats = runScenario().get("throw");
      HttpStats http = HttpStats.get(stats);
      assertThat(stats.requestCount).isEqualTo(1);
      // handleEnd was not invoked and handleThrowable cannot record response
      // because it does not know if the complete physical response was received.
      assertThat(stats.responseCount).isEqualTo(0);
      assertThat(http.status_2xx).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(http.cacheHits).isEqualTo(0);
      assertThat(stats.connectionErrors).isEqualTo(0);
      assertThat(stats.internalErrors).isEqualTo(1);
      assertThat(stats.errors()).isEqualTo(1);
   }

   @Test
   public void testMalformedJsonInFragmentedBody() {
      scenario().initialSequence("malformed-json")
            .step(SC).httpRequest(HttpMethod.GET).path("/malformed-json")
            .handler().body(new CheckProcessor.Builder().json("{\"value\":false}"))
            .stopOnInvalid(false).endHandler().endStep();

      StatisticsSnapshot stats = runScenario().get("malformed-json");
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.internalErrors).isEqualTo(0);
      assertThat(stats.connectionErrors).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(0);
   }

   @Test
   public void testEqualToInFragmentedBody() {
      StatisticsSnapshot stats = runCheck("valid-equal", "/valid-equal",
            new CheckProcessor.Builder().equalTo("response body"));
      assertValid(stats);
   }

   @Test
   public void testRegexInFragmentedBody() {
      StatisticsSnapshot stats = runCheck("valid-regex", "/valid-regex",
            new CheckProcessor.Builder().regex("prefix-.*-suffix"));
      assertValid(stats);
   }

   @Test
   public void testJsonInFragmentedBody() {
      StatisticsSnapshot stats = runCheck("valid-json", "/valid-json",
            new CheckProcessor.Builder().json("{\"message\":\"ok\",\"values\":[1,true]}"));
      assertValid(stats);
   }

   @Test
   public void testCheckHonorsStopOnInvalid() {
      scenario().initialSequence("stop-on-invalid")
            .step(SC).httpRequest(HttpMethod.GET).path("/get200")
            .handler().body(new CheckProcessor.Builder().equalTo("expected"))
            .stopOnInvalid(true).endHandler().endStep()
            .step(SC).httpRequest(HttpMethod.GET).path("/get200").endStep();

      StatisticsSnapshot stats = runScenario().get("stop-on-invalid");
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(stats.internalErrors).isEqualTo(0);
   }

   @Test
   public void testThrowInCompletionHandler() {
      scenario().initialSequence("throw")
            .step(SC).httpRequest(HttpMethod.GET).path("/get200")
            .handler().onCompletion(() -> session -> {
               throw new RuntimeException("Induced failure");
            }).endHandler().endStep();

      StatisticsSnapshot stats = runScenario().get("throw");
      HttpStats http = HttpStats.get(stats);
      assertThat(stats.requestCount).isEqualTo(1);
      // contrary to testThrowInBodyHandler the response is already recorded before the completion handlers run
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(http.status_2xx).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(1);
      assertThat(http.cacheHits).isEqualTo(0);
      assertThat(stats.connectionErrors).isEqualTo(0);
      assertThat(stats.internalErrors).isEqualTo(1);
      assertThat(stats.errors()).isEqualTo(1);
   }

   private StatisticsSnapshot runCheck(String name, String path, CheckProcessor.Builder check) {
      scenario().initialSequence(name)
            .step(SC).httpRequest(HttpMethod.GET).path(path)
            .handler().body(check).endHandler().endStep();
      return runScenario().get(name);
   }

   private void assertValid(StatisticsSnapshot stats) {
      assertThat(stats.requestCount).isEqualTo(1);
      assertThat(stats.responseCount).isEqualTo(1);
      assertThat(stats.invalid).isEqualTo(0);
      assertThat(stats.internalErrors).isEqualTo(0);
      assertThat(stats.connectionErrors).isEqualTo(0);
      assertThat(stats.errors()).isEqualTo(0);
   }
}
