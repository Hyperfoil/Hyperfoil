package io.hyperfoil.http.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;

public class HttpCacheEnablementTest extends BaseHttpScenarioTest {

   @Override
   protected void initRouter() {
      router.route("/ok").handler(ctx -> vertx.setTimer(5, id -> ctx.response().end()));
      router.route("/error").handler(ctx -> vertx.setTimer(5, id -> ctx.response().setStatusCode(400).end()));
      router.route("/close").handler(ctx -> ctx.response().reset());
   }

   @Test
   public void testOkWithoutCacheHttp1x() {
      http().useHttpCache(false)
            .sharedConnections(1);
      testSingle("/ok", false);
   }

   @Test
   public void testOkWithCacheHttp1x() {
      http().useHttpCache(true)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testOkWithoutCacheByDefaultHttp1x() {
      http().sharedConnections(1);
      testSingle("/ok", false);
   }

   private void testSingle(String path, boolean isUsingCache) {
      AtomicReference<HttpCache> httpCacheRef = new AtomicReference<>();
      benchmarkBuilder.addPhase("test").atOnce(1).duration(10).scenario()
            .initialSequence("test")
            .step(session -> {
               httpCacheRef.set(HttpCache.get(session));
               return true;
            })
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET).path(path).endStep();

      TestStatistics requestStats = new TestStatistics();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmarkBuilder.build(), requestStats,
            null, null);
      runner.run();

      assertThat(httpCacheRef.get() != null).isEqualTo(isUsingCache);
   }

   private HttpBuilder http() {
      return benchmarkBuilder.plugin(HttpPluginBuilder.class).http();
   }
}
