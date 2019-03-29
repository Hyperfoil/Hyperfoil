package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;

@RunWith(VertxUnitRunner.class)
public class TwoServersTest extends BaseScenarioTest {
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
         benchmarkBuilder.simulation()
               .http("http://localhost:" + secondServer.actualPort()).endHttp();
      }));
   }

   @Test
   public void test() {
      scenario().initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET)
               .path("/test")
               .statistics("server1")
            .endStep()
            .step(SC).httpRequest(HttpMethod.GET)
               .baseUrl("http://localhost:" + secondServer.actualPort())
               .path("/test")
               .statistics("server2")
               .handler()
                  .onCompletion(s -> latch.countDown())
               .endHandler()
            .endStep()
            .step(SC).awaitAllResponses();

      Map<String, List<StatisticsSnapshot>> stats = runScenario();
      StatisticsSnapshot s1 = assertSingleItem(stats.get("server1"));
      assertThat(s1.status_2xx).isEqualTo(1);
      StatisticsSnapshot s2 = assertSingleItem(stats.get("server2"));
      assertThat(s2.status_3xx).isEqualTo(1);
   }
}
