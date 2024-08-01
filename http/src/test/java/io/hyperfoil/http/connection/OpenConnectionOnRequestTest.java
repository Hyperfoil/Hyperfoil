package io.hyperfoil.http.connection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.netty.handler.codec.http.HttpHeaderNames;

public class OpenConnectionOnRequestTest extends BaseHttpScenarioTest {
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
      http.connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST);
   }

   @Test
   public void test() {
      Set<HttpConnection> connections = new HashSet<>();
      //@formatter:off
      benchmarkBuilder.addPhase("test").always(10).duration(1000).scenario().initialSequence("test")
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
               .path("/")
               .handler().status((request, status) -> {
                  assertFalse(connections.contains(request.connection()));
                  connections.add(request.connection());
               }).endHandler()
            .endStep()
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
               .path("/")
               .headers().header(HttpHeaderNames.CACHE_CONTROL, "no-cache").endHeaders()
               .handler().status((request, status) -> {
                  assertTrue(connections.contains(request.connection()));
               }).endHandler()
            .endStep();
      //@formatter:on

      runScenario();
      assertTrue(connections.size() >= 10);
   }
}
