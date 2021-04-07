package io.hyperfoil.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.statistics.HttpStats;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;

@RunWith(VertxUnitRunner.class)
public class CompressionTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route("/short").handler(ctx -> {
         if (checkAcceptEncoding(ctx)) {
            return;
         }
         ctx.response().end("Short message to be encoded.");
      });
      router.route("/long").handler(ctx -> {
         if (checkAcceptEncoding(ctx)) {
            return;
         }
         StringBuilder sb = new StringBuilder();
         ThreadLocalRandom random = ThreadLocalRandom.current();
         for (int i = 0; i < 10000; ++i) {
            sb.append((char) random.nextInt('A', 'Z' + 1));
         }
         ctx.response().end(sb.toString());
      });
   }

   @Override
   protected boolean useCompression() {
      return true;
   }

   private boolean checkAcceptEncoding(RoutingContext ctx) {
      if (!"gzip".equalsIgnoreCase(ctx.request().getHeader(HttpHeaders.ACCEPT_ENCODING))) {
         ctx.response().setStatusCode(400).end("Expected accept-encoding header");
         return true;
      }
      return false;
   }

   @Test
   public void test() {
      Benchmark benchmark = loadScenario("scenarios/CompressionTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      validateStats(stats.get("short"));
      validateStats(stats.get("long"));
   }

   private void validateStats(StatisticsSnapshot snapshot) {
      assertThat(snapshot.requestCount).isEqualTo(1);
      assertThat(HttpStats.get(snapshot).status_2xx).isEqualTo(1);
      assertThat(snapshot.invalid).isEqualTo(0);
   }
}
