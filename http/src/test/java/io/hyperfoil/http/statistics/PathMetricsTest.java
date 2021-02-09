package io.hyperfoil.http.statistics;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.steps.PathMetricSelector;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpMethod;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class PathMetricsTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route("/foo.js").handler(ctx -> ctx.response().setStatusCode(200).end());
      router.route("/bar.php").handler(ctx -> ctx.response().setStatusCode(200).end());
      router.route("/goo.css").handler(ctx -> ctx.response().setStatusCode(200).end());
   }

   @Test
   public void test() {
      AtomicInteger counter = new AtomicInteger(0);
      PathMetricSelector selector = new PathMetricSelector();
      selector.nextItem(".*\\.js");
      selector.nextItem("(.*\\.php).* -> $1");
      selector.nextItem("-> others");
      scenario(3).initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET)
            .path(s -> {
               switch (counter.getAndIncrement()) {
                  case 0:
                     return "/foo.js";
                  case 1:
                     return "/bar.php?foo=bar";
                  case 2:
                     return "/goo.css";
                  default:
                     throw new IllegalStateException();
               }
            })
            .metric(selector)
            .endStep();

      Map<String, StatisticsSnapshot> stats = runScenario();
      assertThat(stats.get("/foo.js").requestCount).isEqualTo(1);
      assertThat(stats.get("/bar.php").requestCount).isEqualTo(1);
      assertThat(stats.get("others").requestCount).isEqualTo(1);
   }

}
