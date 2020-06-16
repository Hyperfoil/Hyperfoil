package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.handlers.RangeStatusValidator;
import io.hyperfoil.core.handlers.RecordHeaderTimeHandler;
import io.hyperfoil.core.steps.SetAction;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.handler.BodyHandler;

@RunWith(VertxUnitRunner.class)
public class HttpRequestTest extends BaseScenarioTest {
   @Override
   protected void initRouter() {
      router.route().handler(BodyHandler.create());
      router.post("/test").handler(ctx -> {
         String expect = ctx.request().getParam("expect");
         String body = ctx.getBodyAsString();
         if (expect == null) {
            ctx.response().setStatusCode(400).end();
            return;
         }
         ctx.response().setStatusCode(expect.equals(body) ? 200 : 412).end();
      });
      router.get("/status").handler(ctx -> {
         String s = ctx.request().getParam("s");
         ctx.response().setStatusCode(Integer.parseInt(s)).end();
      });
      router.get("/test").handler(ctx -> {
         ctx.response().putHeader("x-foo", "5");
         String expectHeader = ctx.request().getParam("expectHeader");
         if (expectHeader != null) {
            String[] headerValue = expectHeader.split(":", 2);
            String actualValue = ctx.request().getHeader(headerValue[0]);
            ctx.response().setStatusCode(Objects.equals(actualValue, headerValue[1]) ? 200 : 412);
         }
         ctx.response().end();
      });
   }

   private StatusHandler verifyStatus(TestContext ctx) {
      return (request, status) -> {
         if (status != 200) {
            ctx.fail("Status is " + status);
         }
      };
   }

   @Test
   public void testStringBody(TestContext ctx) {
      // @formatter:off
      scenario(10)
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body("bar")
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();
      // @formatter:on
      runScenario();
   }

   @Test
   public void testStringFromVar(TestContext ctx) {
      // @formatter:off
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(SC).action(new SetAction.Builder()
                  .var("x")
                  .value("bar"))
               .step(SC).httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body().fromVar("x").endBody()
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();
      // @formatter:on
      runScenario();
   }

   @Test
   public void testLongChineseStringFromVar(TestContext ctx) throws UnsupportedEncodingException {
      StringBuilder sb = new StringBuilder();
      ThreadLocalRandom random = ThreadLocalRandom.current();
      for (int i = 0; i < 257; ++i) {
         sb.append((char) random.nextInt(0x4E00, 0x9FA5));
      }
      String chineseStr = sb.toString();
      // @formatter:off
      scenario()
            .objectVar("x")
            .initialSequence("test")
            .step(SC).action(new SetAction.Builder()
               .var("x")
               .value(chineseStr))
            .step(SC).httpRequest(HttpMethod.POST)
               .path("/test?expect=" + URLEncoder.encode(chineseStr, StandardCharsets.UTF_8.name()))
               .body().fromVar("x").endBody()
               .handler().status(verifyStatus(ctx)).endHandler()
            .endStep();
      // @formatter:on
      runScenario();
   }

   @Test
   public void testPattern(TestContext ctx) {
      // @formatter:off
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(SC).action(new SetAction.Builder()
                  .var("x")
                  .value("bar"))
               .step(SC).httpRequest(HttpMethod.POST)
                  .path().pattern("/test?expect=${x}").end()
                  .body("bar")
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();
      // @formatter:off
      runScenario();
   }

   @Test
   public void testStatusValidator() {
      // @formatter:off
      scenario()
            .initialSequence("expectOK")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/status?s=205")
                  .handler()
                     .status(new RangeStatusValidator(205, 205))
                     .endHandler()
                  .endStep()
               .endSequence()
            .initialSequence("expectFail")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/status?s=406")
                  .handler()
                     .status(new RangeStatusValidator(200, 299))
                     .endHandler()
                  .endStep()
               .endSequence()
            .endScenario().endPhase()
            .ergonomics()
               .autoRangeCheck(false)
               .stopOnInvalid(false);
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot snapshot0 = stats.get("expectOK");
      StatisticsSnapshot snapshot1 = stats.get("expectFail");
      assertThat(snapshot0.status_2xx).isEqualTo(1);
      assertThat(snapshot0.status_4xx).isEqualTo(0);
      assertThat(snapshot1.status_2xx).isEqualTo(0);
      assertThat(snapshot1.status_4xx).isEqualTo(1);
      assertThat(snapshot0.invalid).isEqualTo(0);
      assertThat(snapshot1.invalid).isEqualTo(1);
   }

   @Test
   public void testRecordHeaderValueHandler() {
      // @formatter:off
      scenario()
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test")
                  .handler()
                     .header(new RecordHeaderTimeHandler.Builder().header("x-foo").unit("ms"))
                  .endHandler()
               .endStep()
            .endSequence();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      assertThat(stats.get("test").requestCount).isEqualTo(1);
      assertThat(stats.get("x-foo").histogram.getCountAtValue(TimeUnit.MILLISECONDS.toNanos(5))).isEqualTo(1);
   }

   @Test
   public void testRequestHeaders() {
      // @formatter:off
      scenario()
            .initialSequence("testFromVar")
               .step(SC).action(new SetAction.Builder()
                  .var("foo")
                  .value("bar"))
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test?expectHeader=Authorization:bar")
                  .headers()
                     .withKey("Authorization")
                        .fromVar("foo")
                     .end()
                  .endHeaders()
               .endStep()
            .endSequence()
            .initialSequence("testPattern")
               .step(SC).action(new SetAction.Builder()
                  .var("foo")
                  .value("bar"))
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test?expectHeader=Authorization:xxxbarxxx")
                  .headers()
                     .withKey("Authorization")
                        .pattern("xxx${foo}xxx")
                     .end()
                  .endHeaders()
               .endStep()
            .endSequence();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      assertThat(stats.get("testFromVar").status_2xx).isEqualTo(1);
      assertThat(stats.get("testPattern").status_2xx).isEqualTo(1);
   }
}
