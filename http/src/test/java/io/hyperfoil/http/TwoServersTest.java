package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
public class TwoServersTest extends HttpScenarioTest {
   CountDownLatch latch = new CountDownLatch(1);
   HttpServer secondServer;

   @Override
   protected void initRouter() {
      router.route("/test").handler(ctx -> {
         try {
            latch.await(10, TimeUnit.SECONDS);
         } catch (InterruptedException e) {
         }
         ctx.response().setStatusCode(200).end();
      });
   }

   @Override
   public void before(TestContext ctx) {
      super.before(ctx);
      Router secondRouter = Router.router(vertx);
      secondRouter.route("/test").handler(context -> context.response().setStatusCode(300).end());
      secondServer = vertx.createHttpServer().requestHandler(secondRouter)
            .listen(0, "localhost", ctx.asyncAssertSuccess(srv -> {
               benchmarkBuilder.plugin(HttpPluginBuilder.class)
                     .http("http://localhost:" + secondServer.actualPort()).endHttp();
            }));
   }

   @Test
   public void test() {
      // @formatter:off
      scenario().initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET)
               .path("/test")
               .sync(false)
               .metric("server1")
            .endStep()
            .step(SC).httpRequest(HttpMethod.GET)
               .authority("localhost:" + secondServer.actualPort())
               .path("/test")
               .sync(false)
               .metric("server2")
               .handler()
                  .onCompletion(s -> latch.countDown())
               .endHandler()
            .endStep()
            .step(SC).awaitAllResponses();
      // @formatter:on
      Map<String, StatisticsSnapshot> stats = runScenario();
      StatisticsSnapshot s1 = stats.get("server1");
      assertThat(s1.status_2xx).isEqualTo(1);
      StatisticsSnapshot s2 = stats.get("server2");
      assertThat(s2.status_3xx).isEqualTo(1);
   }
}
