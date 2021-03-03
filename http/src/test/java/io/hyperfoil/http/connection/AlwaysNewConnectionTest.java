package io.hyperfoil.http.connection;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class AlwaysNewConnectionTest extends HttpScenarioTest {
   @Override
   protected void initRouter() {
      router.get("/").handler(ctx -> ctx.response().end());
   }

   @Override
   protected int threads() {
      return 1;
   }

   @Override
   protected void initHttp(HttpBuilder http) {
      http.connectionStrategy(ConnectionStrategy.ALWAYS_NEW);
   }

   @Test
   public void test(TestContext ctx) {
      Set<HttpConnection> connections = new HashSet<>();
      StatusHandler statusHandler = (request, status) -> {
         ctx.assertFalse(connections.contains(request.connection()));
         connections.add(request.connection());
      };
      //@formatter:off
      benchmarkBuilder.addPhase("test").always(10).duration(1000).scenario().initialSequence("test")
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
               .path("/")
               .handler().status(statusHandler).endHandler()
            .endStep()
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
               .path("/")
               .headers().header(HttpHeaderNames.CACHE_CONTROL, "no-cache").endHeaders()
               .handler().status(statusHandler).endHandler()
            .endStep();
      //@formatter:on

      runScenario();
      ctx.assertTrue(connections.size() >= 10);
   }
}
