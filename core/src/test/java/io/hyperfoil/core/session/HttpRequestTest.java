package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.http.StatusExtractor;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.extractors.RangeStatusValidator;
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
         }
         ctx.response().setStatusCode(expect.equals(body) ? 200 : 412).end();
      });
      router.get("/status").handler(ctx -> {
         String s = ctx.request().getParam("s");
         ctx.response().setStatusCode(Integer.parseInt(s)).end();
      });
   }

   private StatusExtractor verifyStatus(TestContext ctx) {
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
               .step().httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body("bar")
                  .handler().statusExtractor(verifyStatus(ctx))
                  .endHandler().endStep()
               .step().awaitAllResponses();

      runScenario();
   }

   @Test
   public void testStringFromVar(TestContext ctx) {
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(s -> {
                  s.setObject("x", "bar");
                  return true;
               })
               .step().httpRequest(HttpMethod.POST)
                  .path("/test?expect=bar")
                  .body().var("x").endBody()
                  .handler().statusExtractor(verifyStatus(ctx))
                  .endHandler().endStep()
               .step().awaitAllResponses();

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
            .step(s -> {
               s.setObject("x", chineseStr);
               return true;
            })
            .step().httpRequest(HttpMethod.POST)
            .path("/test?expect=" + URLEncoder.encode(chineseStr, StandardCharsets.UTF_8.name()))
            .body().var("x").endBody()
            .handler().statusExtractor(verifyStatus(ctx))
            .endHandler().endStep()
            .step().awaitAllResponses();

      runScenario();
   }

   @Test
   public void testPattern(TestContext ctx) {
      scenario()
            .objectVar("x")
            .initialSequence("test")
               .step(s -> {
                  s.setObject("x", "bar");
                  return true;
               })
               .step().httpRequest(HttpMethod.POST)
                  .path().pattern("/test?expect=${x}").end()
                  .body("bar")
                  .handler().statusExtractor(verifyStatus(ctx))
                  .endHandler().endStep()
               .step().awaitAllResponses();

      runScenario();
   }

   @Test
   public void testStatusValidator(TestContext ctx) {
      scenario()
            .initialSequence("expectOK")
               .step().httpRequest(HttpMethod.GET)
                  .path("/status?s=205")
                  .handler()
                     .statusValidator(new RangeStatusValidator(205, 205))
                     .endHandler()
                  .endStep()
               .step().awaitAllResponses()
               .endSequence()
            .initialSequence("expectFail")
               .step().httpRequest(HttpMethod.GET)
                  .path("/status?s=406")
                  .handler()
                     .statusValidator(new RangeStatusValidator(200, 299))
                     .endHandler()
                  .endStep()
               .step().awaitAllResponses()
               .endSequence();

      List<Session> sessions = runScenario();
      Session session = sessions.iterator().next();
      StatisticsSnapshot snapshot0 = session.statistics(0).snapshot();
      StatisticsSnapshot snapshot1 = session.statistics(1).snapshot();
      assertThat(snapshot0.status_2xx).isEqualTo(1);
      assertThat(snapshot0.status_4xx).isEqualTo(0);
      assertThat(snapshot1.status_2xx).isEqualTo(0);
      assertThat(snapshot1.status_4xx).isEqualTo(1);
      assertThat(session.validatorResults().statusValid()).isEqualTo(1);
      assertThat(session.validatorResults().statusInvalid()).isEqualTo(1);
   }
}
