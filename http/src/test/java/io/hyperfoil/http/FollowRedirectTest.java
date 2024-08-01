package io.hyperfoil.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.api.FollowRedirect;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

public class FollowRedirectTest extends BaseHttpScenarioTest {
   private final AtomicInteger redirects = new AtomicInteger();
   private final AtomicInteger notFound = new AtomicInteger();

   @BeforeEach
   public void resetRedirects() {
      redirects.set(0);
      notFound.set(0);
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
      router.route("/redirect/me/relatively")
            .handler(ctx -> ctx.response().putHeader(HttpHeaders.LOCATION, "elsewhere").setStatusCode(302).end());
      router.route("/redirect/me/elsewhere").handler(ctx -> ctx.response()
            .end("<html><head><meta http-equiv=\"refresh\" content=\"0; URL=../theEnd\" /></head></html>"));
      router.route("/redirect/theEnd").handler(ctx -> ctx.response().end("Final destination"));
   }

   private void redirectViaHtml(RoutingContext ctx) {
      if (!ensureHeaders(ctx)) {
         return;
      }
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (random.nextBoolean()) {
         String refreshContent = random.nextInt(2) + "; url=" + target(ctx);
         ctx.response().end("<html><head><meta http-equiv=\"refresh\" content=\"" + refreshContent + "\" /></head></html>");
         redirects.incrementAndGet();
      } else {
         if (random.nextBoolean()) {
            ctx.response().end("this is the response");
         } else {
            notFound.incrementAndGet();
            ctx.response().setStatusCode(404).end("Not Found (sort of)");
         }
      }
   }

   private void redirectViaLocation(RoutingContext ctx) {
      if (!ensureHeaders(ctx)) {
         return;
      }
      ThreadLocalRandom random = ThreadLocalRandom.current();
      if (random.nextBoolean()) {
         ctx.response().putHeader(HttpHeaders.LOCATION, target(ctx)).setStatusCode(303).end();
         redirects.incrementAndGet();
      } else {
         if (random.nextBoolean()) {
            ctx.response().end("this is the response");
         } else {
            notFound.incrementAndGet();
            ctx.response().setStatusCode(404).end("Not Found (sort of)");
         }
      }
   }

   private String target(RoutingContext ctx) {
      boolean allowRecursion = "yes".equals(ctx.request().getParam("allowRecurse"));
      return allowRecursion && ThreadLocalRandom.current().nextBoolean() ? ctx.request().path() + "?allowRecurse=yes"
            : "/somewhereElse";
   }

   private boolean ensureHeaders(io.vertx.ext.web.RoutingContext ctx) {
      if (!ctx.request().getHeader("x-preserve").equals("repeat me with redirect")) {
         log.error("Missing header x-preserve");
         ctx.response().setStatusCode(500).end("Missing or incorrect x-preserve header");
         return false;
      }
      return true;
   }

   @Test
   public void testManual() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_manual.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .mapToInt(p -> ((Model.AtOnce) p.model).users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      HttpStats redirectMe = HttpStats.get(stats.get("redirectMe"));
      assertThat(redirectMe.status_3xx).isEqualTo(redirects.get());
      assertThat(redirectMe.status_2xx).isEqualTo(users - redirects.get() - notFound.get());
      assertThat(redirectMe.status_4xx).isEqualTo(notFound.get());
      HttpStats redirectMe_redirect = HttpStats.get(stats.get("redirectMe_redirect"));
      assertThat(redirectMe_redirect.status_2xx).isEqualTo(redirects.get());
   }

   @Test
   public void testLocation() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_location.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .mapToInt(p -> ((Model.AtOnce) p.model).users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      HttpStats redirectMe = HttpStats.get(stats.get("redirectMe"));
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirectMe.status_2xx).isEqualTo(users - notFound.get());
         assertThat(redirectMe.status_3xx).isEqualTo(0);
         assertThat(redirectMe.status_4xx).isEqualTo(notFound.get());
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         HttpStats redirectStats = HttpStats.get(stats.get(redirectMetric));
         assertThat(redirectMe.status_2xx + redirectStats.status_2xx).isEqualTo(users - notFound.get());
         assertThat(redirectMe.status_3xx + redirectStats.status_3xx).isEqualTo(redirects.get());
         assertThat(redirectMe.status_4xx + redirectStats.status_4xx).isEqualTo(notFound.get());
      }
   }

   @Test
   public void testHtmlOnly() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_html.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .mapToInt(p -> ((Model.AtOnce) p.model).users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      HttpStats redirectMe = HttpStats.get(stats.get("redirectMe"));
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirects.get()).isEqualTo(0);
         assertThat(redirectMe.status_2xx).isEqualTo(users - notFound.get());
         assertThat(redirectMe.status_4xx).isEqualTo(notFound.get());
      } else {
         HttpStats redirectStats = HttpStats.get(stats.get(redirectMetric));
         assertThat(redirectStats.status_2xx + redirectMe.status_2xx).isEqualTo(users + redirects.get() - notFound.get());
         assertThat(redirectStats.status_3xx).isEqualTo(0);
         assertThat(redirectStats.status_4xx + redirectMe.status_4xx).isEqualTo(notFound.get());
      }
      assertThat(redirectMe.status_3xx).isEqualTo(0);
   }

   @Test
   public void testAlways() {
      Benchmark benchmark = loadScenario("scenarios/FollowRedirectTest_always.hf.yaml");
      int users = benchmark.phases().stream().filter(p -> "testPhase".equals(p.name()))
            .mapToInt(p -> ((Model.AtOnce) p.model).users).findFirst().orElse(0);
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      HttpStats redirectMe = HttpStats.get(stats.get("redirectMe"));
      String redirectMetric = stats.keySet().stream().filter(m -> !m.equals("redirectMe")).findFirst().orElse(null);
      if (redirectMetric == null) {
         // rare case when we'd not get any redirects
         assertThat(redirectMe.status_2xx).isEqualTo(users - notFound.get());
         assertThat(redirectMe.status_3xx).isEqualTo(0);
         assertThat(redirectMe.status_4xx).isEqualTo(notFound.get());
         assertThat(redirects.get()).isEqualTo(0);
      } else {
         HttpStats redirectStats = HttpStats.get(stats.get(redirectMetric));
         assertThat(redirectMe.status_2xx + redirectMe.status_3xx).isLessThanOrEqualTo(users)
               .isGreaterThanOrEqualTo(users - notFound.get());
         assertThat(redirectStats.status_2xx + redirectStats.status_3xx).isLessThanOrEqualTo(redirects.get())
               .isGreaterThanOrEqualTo(redirects.get() - notFound.get());
         assertThat(redirectStats.status_4xx + redirectMe.status_4xx).isEqualTo(notFound.get());
      }
   }

   @Test
   public void testRelative() {
      scenario().initialSequence("test").step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
            .path("/redirect/me/relatively")
            .handler().followRedirect(FollowRedirect.ALWAYS).endHandler();

      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot testStats = stats.get("test");
      assertThat(HttpStats.get(testStats).status_3xx).isEqualTo(1);
      assertThat(testStats.responseCount).isEqualTo(1);
      StatisticsSnapshot otherStats = stats.entrySet().stream()
            .filter(e -> !e.getKey().equals("test")).map(Map.Entry::getValue).findFirst().orElse(null);
      assertThat(HttpStats.get(otherStats).status_2xx).isEqualTo(2);
   }
}
