package io.hyperfoil.http.html;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.http.HttpScenarioTest;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class TagAttributeHandlerTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route("/foobar/index.html").handler(ctx -> serveResourceChunked(ctx, "data/TagAttributeHandlerTest_index.html"));
   }

   @Test
   public void test() {
      Benchmark benchmark = loadScenario("scenarios/TagAttributeHandlerTest.hf.yaml");
      runScenario(benchmark);
   }
}