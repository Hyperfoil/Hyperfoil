package io.hyperfoil.core.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.session.BaseScenarioTest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class FollowRedirectTest extends BaseScenarioTest {
   private final AtomicInteger redirects = new AtomicInteger();

   @Before
   public void resetRedirects() {
      redirects.set(0);
   }

   @Override
   protected void initRouter() {
      router.route().handler(ctx -> {
         ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "no-store");
         ctx.next();
      });
      router.route("/redirectMeViaLocation").handler(ctx -> {
         if (!ensureHeaders(ctx)) {
            return;
         }
         if (ThreadLocalRandom.current().nextBoolean()) {
            boolean allowRecursion = "yes".equals(ctx.request().getParam("allowRecurse"));
            String target = allowRecursion && ThreadLocalRandom.current().nextBoolean() ?
                  "/redirectMeViaLocation?allowRecurse=yes" : "/somewhereElse";
            ctx.response().putHeader(HttpHeaders.LOCATION, target).setStatusCode(303).end();
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
         log.error("Missing header x-preserve");
         ctx.response().setStatusCode(500).write("Missing or incorrect x-preserve header").end();
         return false;
      }
      return true;
   }

   @Test
   public void testManual() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_manual.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .map(Phase.AtOnce.class::cast).mapToInt(p -> p.users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot locationManual = stats.get("locationManual");
      assertThat(locationManual.status_3xx).isEqualTo(redirects.get());
      assertThat(locationManual.status_2xx).isEqualTo(users - redirects.get());
      StatisticsSnapshot locationManual_redirect = stats.get("locationManual_redirect");
      assertThat(locationManual_redirect.status_2xx).isEqualTo(redirects.get());
   }

   @Test
   public void testAutomatic() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_automatic.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .map(Phase.AtOnce.class::cast).mapToInt(p -> p.users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot locationAutomatic = stats.get("locationAutomatic");
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("locationAutomatic")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(locationAutomatic.status_2xx).isEqualTo(users);
         assertThat(locationAutomatic.status_3xx).isEqualTo(0);
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         StatisticsSnapshot redirectStats = stats.get(redirectMetric);
         assertThat(locationAutomatic.status_3xx + redirectStats.status_3xx).isEqualTo(redirects.get());
         assertThat(locationAutomatic.status_2xx + redirectStats.status_2xx).isEqualTo(users);
      }
   }
}
