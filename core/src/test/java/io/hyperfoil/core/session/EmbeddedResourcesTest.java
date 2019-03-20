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
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EmbeddedResourcesTest extends BaseScenarioTest {
   @Override
   protected Benchmark benchmark() {
      try {
         InputStream config = getClass().getClassLoader().getResourceAsStream("scenarios/downloadRefs.yaml");
         return BenchmarkParser.instance().buildBenchmark(config);
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      }
   }

   @Override
   protected void initRouter() {
      router.route("/foobar/index.html").handler(ctx -> {
         try {
            InputStream index = getClass().getClassLoader().getResourceAsStream("data/EmbeddedResourcesTest_index.html");
            ctx.response().end(Util.toString(index));
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
      assertThat(stats.size()).isEqualTo(5);
      for (Map.Entry<String, List<StatisticsSnapshot>> entry : stats.entrySet()) {
         String name = entry.getKey();
         int hits;
         if (name.equals("automatic") || name.equals("manual")) {
            hits = 1;
         } else {
            assertThat(name).matches(".*\\.(css|js|ico|php)");
            hits = 2;
         }
         List<StatisticsSnapshot> list = entry.getValue();
         assertThat(list.size()).isEqualTo(1);
         StatisticsSnapshot snapshot = list.iterator().next();
         assertThat(snapshot.requestCount).isEqualTo(hits);
         assertThat(snapshot.status_2xx).isEqualTo(hits);
      }
   }
}
