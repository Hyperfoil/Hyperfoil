package io.sailrocket.core.session;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.StatusExtractor;
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
}
