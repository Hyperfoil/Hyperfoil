package io.hyperfoil.http.html;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.http.BaseHttpScenarioTest;

public class TagAttributeHandlerTest extends BaseHttpScenarioTest {
   @Override
   protected void initRouter() {
      router.route("/foobar/index.html")
            .handler(ctx -> serveResourceChunked(ctx, "data/TagAttributeHandlerTest_index.html"));
   }

   @Test
   public void test() {
      Benchmark benchmark = loadScenario("scenarios/TagAttributeHandlerTest.hf.yaml");
      runScenario(benchmark);
   }
}