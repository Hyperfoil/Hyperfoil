package io.hyperfoil.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
public class JsonReplaceTest extends BaseHttpScenarioTest {
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
      Benchmark benchmark = loadScenario("scenarios/JsonReplaceTest.hf.yaml");
      Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
      stats.values().forEach(s -> assertThat(s.errors()).isEqualTo(0));
      assertThat(stats.values().stream().mapToInt(s -> s.responseCount).sum()).isEqualTo(2);
   }
}
