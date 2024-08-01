package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.statistics.HttpStats;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class TwoServersTest extends BaseHttpScenarioTest {
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

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      Router secondRouter = Router.router(vertx);
      secondRouter.route("/test").handler(context -> context.response().setStatusCode(300).end());
      var secondServerLatch = new CountDownLatch(1);
      secondServer = vertx.createHttpServer().requestHandler(secondRouter)
            .listen(0, "localhost", ctx.succeeding(srv -> {
               benchmarkBuilder.plugin(HttpPluginBuilder.class)
                     .http("http://localhost:" + srv.actualPort()).endHttp();
               ctx.completeNow();
               secondServerLatch.countDown();
            }));
      try {
         secondServerLatch.await(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         ctx.failNow(e);
      }
   }

   @Test
   public void testTwoServers() {
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
      assertThat(HttpStats.get(s1).status_2xx).isEqualTo(1);
      StatisticsSnapshot s2 = stats.get("server2");
      assertThat(HttpStats.get(s2).status_3xx).isEqualTo(1);
   }

   @Test
   public void testMultiHostWithoutAuthorityFail() {
      // Test that a multi-host HTTP configuration is not accepted when steps does not define a host to run.
      // See: https://github.com/Hyperfoil/Hyperfoil/issues/315

      // Override the default builder creation by the test.
      benchmarkBuilder = BenchmarkBuilder.builder();
      benchmarkBuilder.threads(threads());
      benchmarkBuilder.addPlugin(HttpPluginBuilder::new);

      // Define hosts *without* default HTTP server.
      // Note that, utilizing the YAML configuration there is no way to define the default host.
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:" + server.actualPort())
            .name("host-1");
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:" + secondServer.actualPort())
            .name("host-2");

      // A single step is enough.
      scenario().initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET)
            .path("/test")
            .endStep();

      // Fails to build since we haven't defined the authority for which server to utilize in the step.
      assertThrows(BenchmarkDefinitionException.class, () -> benchmarkBuilder.build());
   }

   @Test
   public void testServersWithSameHostAndPortAndDifferentName() {
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name("myhost1");
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name("myhost2");
      benchmarkBuilder.build();
   }

   @Test
   public void testServersWithSameHostAndPortAndSameName() {
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name("myhost1");
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name("myhost1");
      assertThrows(BenchmarkDefinitionException.class, () -> benchmarkBuilder.build());
   }

   @Test
   public void testServersWithSameHostAndPortAndNoName() {
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name(null);
      benchmarkBuilder.plugin(HttpPluginBuilder.class)
            .http("http://localhost:8080")
            .name(null);
      assertThrows(BenchmarkDefinitionException.class, () -> benchmarkBuilder.build());
   }
}
