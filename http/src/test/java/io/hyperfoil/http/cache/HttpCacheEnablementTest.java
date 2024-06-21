package io.hyperfoil.http.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpCache;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

public class HttpCacheEnablementTest extends HttpScenarioTest {

   @Override
   protected Future<Void> startServer(TestContext ctx, boolean tls, boolean compression) {
      // don't start the server
      return null;
   }

   private void startServer(TestContext ctx) {
      Async async = ctx.async();
      super.startServer(ctx, false, false).onComplete(ctx.asyncAssertSuccess(nil -> async.complete()));
      async.await();
   }

   @Override
   protected void initRouter() {
      router.route("/ok").handler(ctx -> vertx.setTimer(5, id -> ctx.response().end()));
      router.route("/error").handler(ctx -> vertx.setTimer(5, id -> ctx.response().setStatusCode(400).end()));
      router.route("/close").handler(ctx -> ctx.response().reset());
   }

   @Test
   public void testOkWithoutCacheHttp1x(TestContext ctx) {
      startServer(ctx);
      http().useHttpCache(false)
            .sharedConnections(1);
      testSingle("/ok", false);
   }

   @Test
   public void testOkWithCacheHttp1x(TestContext ctx) {
      startServer(ctx);
      http().useHttpCache(true)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testOkWithCacheByDefaultHttp1x(TestContext ctx) {
      startServer(ctx);
      http().sharedConnections(1);
      testSingle("/ok", true);
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
