package io.hyperfoil.http.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.statistics.HttpStats;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EmbeddedResourcesTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route().handler(ctx -> {
         ctx.response().putHeader(HttpHeaders.CACHE_CONTROL, "no-store");
         ctx.next();
      });
      router.route("/foobar/index.html").handler(ctx -> serveResourceChunked(ctx, "data/EmbeddedResourcesTest_index.html"));
      router.route("/styles/style.css").handler(ctx -> ctx.response().end("You've got style!"));
      router.route("/foobar/stuff.js").handler(ctx -> ctx.response().end("alert('Hello world!')"));
      router.route("/generate.php").handler(ctx -> ctx.response().end());
   }

   @Test
   public void test() {
      Benchmark benchmark = loadScenario("scenarios/EmbeddedResourcesTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      assertThat(stats.size()).isEqualTo(6);
      for (Map.Entry<String, StatisticsSnapshot> entry : stats.entrySet()) {
         String name = entry.getKey();
         int hits;
         if (name.equals("automatic") || name.equals("manual") || name.equals("legacy")) {
            hits = 1;
         } else {
            assertThat(name).matches(".*\\.(css|js|ico|php)");
            hits = 3;
         }
         StatisticsSnapshot snapshot = entry.getValue();
         assertThat(snapshot.requestCount).as(name).isEqualTo(hits);
         assertThat(HttpStats.get(snapshot).status_2xx).as(name).isEqualTo(hits);
      }
   }
}
