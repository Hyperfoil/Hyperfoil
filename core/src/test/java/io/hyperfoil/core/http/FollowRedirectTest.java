package io.hyperfoil.core.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class FollowRedirectTest extends BaseScenarioTest {
   private final AtomicInteger redirects = new AtomicInteger();

   @Override
   protected Benchmark benchmark() {
      return loadScenario("scenarios/FollowRedirectTest.hf.yaml");
   }

   @Before
   public void resetRedirects() {
      redirects.set(0);
   }

   @Override
   protected void initRouter() {
      router.route("/redirectMeViaLocation").handler(ctx -> {
         if (!ensureHeaders(ctx)) {
            return;
         }
         if (ThreadLocalRandom.current().nextBoolean()) {
            ctx.response().putHeader(HttpHeaders.LOCATION, "/somewhereElse").setStatusCode(303).end();
            redirects.incrementAndGet();
         } else {
            ctx.response().end("this is the response");
         }
      });
      router.route("/somewhereElse").handler(ctx -> {
         if (!ensureHeaders(ctx)) {
            return;
         }
         ctx.response().end("this is the response");
      });
   }

   private boolean ensureHeaders(io.vertx.ext.web.RoutingContext ctx) {
      if (!ctx.request().getHeader("x-preserve").equals("repeat me with redirect")) {
         ctx.response().setStatusCode(500).write("Missing or incorrect x-preserve header").end();
         return false;
      }
      return true;
   }

   @Test
   public void test() {
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot locationManual = stats.get("locationManual");
      assertThat(locationManual.status_3xx).isEqualTo(redirects.get());
      assertThat(locationManual.status_2xx).isEqualTo(10 - redirects.get());
      String locationManual_redirect = "locationManual_redirect";
      assertThat(stats.get(locationManual_redirect).status_2xx).isEqualTo(redirects.get());
   }
}
