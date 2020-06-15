package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.Util;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EmbeddedResourcesTest extends BaseScenarioTest {
   @Override
   protected Benchmark benchmark() {
      return loadScenario("scenarios/EmbeddedResourcesTest.hf.yaml");
   }

   @Override
   protected void initRouter() {
      router.route("/foobar/index.html").handler(ctx -> {
         try {
            InputStream index = getClass().getClassLoader().getResourceAsStream("data/EmbeddedResourcesTest_index.html");
            String html = Util.toString(index);
            // We'll send the body in two chunks to make sure the code works even if the body is not delivered in one row
            ctx.response().setChunked(true);
            int bodyStartIndex = html.indexOf("<body>");
            ctx.response().write(html.substring(0, bodyStartIndex), result -> vertx.setTimer(100, ignores -> {
               ctx.response().write(html.substring(bodyStartIndex));
               ctx.response().end();
            }));
         } catch (IOException e) {
            ctx.response().setStatusCode(500).end();
         }
      });
      router.route("/styles/style.css").handler(ctx -> ctx.response().end("You've got style!"));
      router.route("/foobar/stuff.js").handler(ctx -> ctx.response().end("alert('Hello world!')"));
      router.route("/generate.php").handler(ctx -> ctx.response().end());
   }

   @Test
   public void test() {
      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      assertThat(stats.size()).isEqualTo(6);
      for (Map.Entry<String, List<StatisticsSnapshot>> entry : stats.entrySet()) {
         String name = entry.getKey();
         int hits;
         if (name.equals("automatic") || name.equals("manual") || name.equals("legacy")) {
            hits = 1;
         } else {
            assertThat(name).matches(".*\\.(css|js|ico|php)");
            hits = 3;
         }
         List<StatisticsSnapshot> list = entry.getValue();
         assertThat(list.size()).isEqualTo(hits);
         for (StatisticsSnapshot snapshot : list) {
            assertThat(snapshot.requestCount).as(name).isEqualTo(1);
            assertThat(snapshot.status_2xx).as(name).isEqualTo(1);
         }
      }
   }
}
