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
import io.vertx.ext.web.RoutingContext;

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
      router.route("/redirectMeViaLocation").handler(this::redirectViaLocation);
      router.route("/redirectMeViaHtml").handler(this::redirectViaHtml);
      router.route("/redirectMeSomehow").handler(ctx -> {
         if (ThreadLocalRandom.current().nextBoolean()) {
            redirectViaLocation(ctx);
         } else {
            redirectViaHtml(ctx);
         }
      });
      router.route("/somewhereElse").handler(ctx -> {
         if (!ensureHeaders(ctx)) {
            return;
         }
         ctx.response().end("this is the response");
      });
   }

   private void redirectViaHtml(RoutingContext ctx) {
      if (!ensureHeaders(ctx)) {
         return;
      }
      if (ThreadLocalRandom.current().nextBoolean()) {
         String refreshContent = ThreadLocalRandom.current().nextInt(2) + "; " + target(ctx);
         ctx.response().end("<html><head><meta http-equiv=\"refresh\" content=\"" + refreshContent + "\" /></head></html>");
         redirects.incrementAndGet();
      } else {
         ctx.response().end("this is the response");
      }
   }

   private void redirectViaLocation(RoutingContext ctx) {
      if (!ensureHeaders(ctx)) {
         return;
      }
      if (ThreadLocalRandom.current().nextBoolean()) {
         ctx.response().putHeader(HttpHeaders.LOCATION, target(ctx)).setStatusCode(303).end();
         redirects.incrementAndGet();
      } else {
         ctx.response().end("this is the response");
      }
   }

   private String target(RoutingContext ctx) {
      boolean allowRecursion = "yes".equals(ctx.request().getParam("allowRecurse"));
      return allowRecursion && ThreadLocalRandom.current().nextBoolean() ?
            ctx.request().path() + "?allowRecurse=yes" : "/somewhereElse";
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
      StatisticsSnapshot redirectMe = stats.get("redirectMe");
      assertThat(redirectMe.status_3xx).isEqualTo(redirects.get());
      assertThat(redirectMe.status_2xx).isEqualTo(users - redirects.get());
      StatisticsSnapshot redirectMe_redirect = stats.get("redirectMe_redirect");
      assertThat(redirectMe_redirect.status_2xx).isEqualTo(redirects.get());
   }

   @Test
   public void testLocation() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_location.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .map(Phase.AtOnce.class::cast).mapToInt(p -> p.users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot redirectMe = stats.get("redirectMe");
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirectMe.status_2xx).isEqualTo(users);
         assertThat(redirectMe.status_3xx).isEqualTo(0);
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         StatisticsSnapshot redirectStats = stats.get(redirectMetric);
         assertThat(redirectMe.status_3xx + redirectStats.status_3xx).isEqualTo(redirects.get());
         assertThat(redirectMe.status_2xx + redirectStats.status_2xx).isEqualTo(users);
      }
   }

   @Test
   public void testHtmlOnly() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_html.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .map(Phase.AtOnce.class::cast).mapToInt(p -> p.users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot redirectMe = stats.get("redirectMe");
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         StatisticsSnapshot redirectStats = stats.get(redirectMetric);
         assertThat(redirectStats.status_2xx).isEqualTo(redirects.get());
         assertThat(redirectStats.status_3xx).isEqualTo(0);
      }
      assertThat(redirectMe.status_2xx).isEqualTo(users);
      assertThat(redirectMe.status_3xx).isEqualTo(0);
   }

   @Test
   public void testAlways() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_always.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .map(Phase.AtOnce.class::cast).mapToInt(p -> p.users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      StatisticsSnapshot redirectMe = stats.get("redirectMe");
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirectMe.status_2xx).isEqualTo(users);
         assertThat(redirectMe.status_3xx).isEqualTo(0);
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         StatisticsSnapshot redirectStats = stats.get(redirectMetric);
         assertThat(redirectMe.status_2xx + redirectMe.status_3xx).isEqualTo(users);
         assertThat(redirectStats.status_2xx + redirectStats.status_3xx).isEqualTo(redirects.get());
      }
   }
}
