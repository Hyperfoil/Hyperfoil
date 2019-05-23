package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.handlers.RangeStatusValidator;
import io.hyperfoil.core.handlers.RecordHeaderTimeHandler;
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
      router.get("/test").handler(ctx -> ctx.response().putHeader("x-foo", "5").end());
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
      scenario()
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body("bar")
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();

      runScenario();
   }

   @Test
   public void testStringFromVar(TestContext ctx) {
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(SC).set()
                  .var("x")
                  .value("bar")
               .endStep()
               .step(SC).httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body().var("x").endBody()
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();

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
      scenario()
            .objectVar("x")
            .initialSequence("test")
            .step(SC).set()
               .var("x")
               .value(chineseStr)
            .endStep()
            .step(SC).httpRequest(HttpMethod.POST)
               .path("/test?expect=" + URLEncoder.encode(chineseStr, StandardCharsets.UTF_8.name()))
               .body().var("x").endBody()
               .handler().status(verifyStatus(ctx)).endHandler()
            .endStep();

      runScenario();
   }

   @Test
   public void testPattern(TestContext ctx) {
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(SC).set()
                  .var("x")
                  .value("bar")
               .endStep()
               .step(SC).httpRequest(HttpMethod.POST)
                  .path().pattern("/test?expect=${x}").end()
                  .body("bar")
                  .handler().status(verifyStatus(ctx))
                  .endHandler()
               .endStep();

      runScenario();
   }

   @Test
   public void testStatusValidator() {
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
               .endSequence();

      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      StatisticsSnapshot snapshot0 = stats.get("expectOK").iterator().next();
      StatisticsSnapshot snapshot1 = stats.get("expectFail").iterator().next();
      assertThat(snapshot0.status_2xx).isEqualTo(1);
      assertThat(snapshot0.status_4xx).isEqualTo(0);
      assertThat(snapshot1.status_2xx).isEqualTo(0);
      assertThat(snapshot1.status_4xx).isEqualTo(1);
      // TODO issue #5
//      assertThat(session.validatorResults().statusValid()).isEqualTo(1);
//      assertThat(session.validatorResults().statusInvalid()).isEqualTo(1);
   }

   @Test
   public void testRecordHeaderValueHandler() {
      scenario()
            .initialSequence("test")
               .step(SC).httpRequest(HttpMethod.GET)
                  .path("/test")
                  .handler()
                     .header(new RecordHeaderTimeHandler.Builder().header("x-foo").unit("ms"))
                  .endHandler()
               .endStep()
            .endSequence();

      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      assertThat(assertSingleItem(stats.get("test")).requestCount).isEqualTo(1);
      List<StatisticsSnapshot> foo = stats.get("x-foo");
      assertThat(foo.size()).isEqualTo(1);
      StatisticsSnapshot snapshot = foo.iterator().next();
      assertThat(snapshot.histogram.getCountAtValue(TimeUnit.MILLISECONDS.toNanos(5))).isEqualTo(1);
   }
}
