package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.handler.BodyHandler;

@RunWith(VertxUnitRunner.class)
public class JsonReplaceTest extends BaseScenarioTest {
   @Override
   protected Benchmark benchmark() {
      return loadScenario("scenarios/JsonReplaceTest.hf.yaml");
   }

   @Override
   protected void initRouter() {
      router.get("/get").handler(ctx -> {
         ctx.response().end(new JsonObject().put("name", "John Doe").put("age", 33).encode());
      });
      router.post("/post").handler(BodyHandler.create()).handler(ctx -> {
         try {
            JsonObject person = ctx.getBodyAsJson();
            assertThat(person.getInteger("age")).isEqualTo(34);
            ctx.response().end();
         } catch (Throwable t) {
            log.error("Assertion failed", t);
            ctx.response().setStatusCode(400).end();
         }
      });
   }

   @Test
   public void test() {
      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      stats.values().stream().flatMap(Collection::stream).forEach(s -> assertThat(s.errors()).isEqualTo(0));
      assertThat(stats.values().stream().flatMap(Collection::stream).mapToInt(s -> s.responseCount).sum()).isEqualTo(2);
   }
}
