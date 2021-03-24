package io.hyperfoil.benchmark.clustering;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.test.Benchmark;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

// Note: this test won't run from IDE (probably) as SshDeployer copies just .jar files for the agents;
// since it inspects classpath for .jars it won't copy the class files in the hyperfoil-clustering module.
// It runs from Maven just fine.
@RunWith(VertxUnitRunner.class)
@Category(Benchmark.class)
public class ClusterTestCase extends BaseClusteredTest {
   private static final int AGENTS = 2;
   private Vertx vertx = Vertx.vertx();

   public static io.hyperfoil.api.config.Benchmark testBenchmark(int agents, int port) {
      BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder().name("test");
      for (int i = 0; i < agents; ++i) {
         benchmarkBuilder.addAgent("agent" + i, "localhost", null);
      }
      // @formatter:off
      benchmarkBuilder
            .addPlugin(HttpPluginBuilder::new)
               .http()
                  .host("localhost").port(port)
                  .sharedConnections(10)
               .endHttp()
            .endPlugin()
            .addPhase("test").always(agents)
               .duration(5000)
               .scenario()
                  .initialSequence("test")
                     .step(SC).httpRequest(HttpMethod.GET)
                        .path("test")
                        .sla().addItem()
                           .meanResponseTime(10, TimeUnit.MILLISECONDS)
                           .limits().add(0.99, TimeUnit.MILLISECONDS.toNanos(100)).end()
                           .errorRatio(0.02)
                           .window(3000, TimeUnit.MILLISECONDS)
                        .endSLA().endList()
                     .endStep()
                  .endSequence()
               .endScenario()
            .endPhase();
      // @formatter:on
      return benchmarkBuilder.build();
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> {
         try {
            Thread.sleep(10);
         } catch (InterruptedException e) {
            e.printStackTrace();  // TODO: Customise this generated block
         }
         req.response().end("test");
      };
   }

   @Before
   public void before(TestContext ctx) {
      super.before(ctx);
      startController(ctx);
   }

   @Test(timeout = 120_000)
   public void startClusteredBenchmarkTest(TestContext ctx) throws IOException, InterruptedException {
      WebClientOptions options = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(8090);
      WebClient client = WebClient.create(this.vertx, options.setFollowRedirects(false));
      Async termination = ctx.async();

      // upload benchmark
      Promise<HttpResponse<Buffer>> uploadPromise = Promise.promise();
      client.post("/benchmark")
            .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/java-serialized-object")
            .sendBuffer(Buffer.buffer(serialize(testBenchmark(AGENTS, httpServer.actualPort()))), ctx.asyncAssertSuccess(uploadPromise::complete));

      Promise<HttpResponse<Buffer>> benchmarkPromise = Promise.promise();
      uploadPromise.future().onSuccess(response -> {
         ctx.assertEquals(response.statusCode(), 204);
         ctx.assertEquals(response.getHeader(HttpHeaders.LOCATION.toString()), "http://localhost:8090/benchmark/test");
         // list benchmarks
         client.get("/benchmark").send(ctx.asyncAssertSuccess(benchmarkPromise::complete));
      });

      Promise<HttpResponse<Buffer>> startPromise = Promise.promise();
      benchmarkPromise.future().onSuccess(response -> {
         ctx.assertEquals(response.statusCode(), 200);
         ctx.assertTrue(new JsonArray(response.bodyAsString()).contains("test"));
         //start benchmark running
         client.get("/benchmark/test/start").send(ctx.asyncAssertSuccess(startPromise::complete));
      });

      startPromise.future().onSuccess(response -> {
         ctx.assertEquals(response.statusCode(), 202);
         String location = response.getHeader(HttpHeaders.LOCATION.toString());
         getStatus(ctx, client, location, termination);
      });
   }

   private void getStatus(TestContext ctx, WebClient client, String location, Async termination) {
      client.get(location).send(ctx.asyncAssertSuccess(response -> {
         ctx.assertEquals(response.statusCode(), 200);
         try {
            JsonObject status = new JsonObject(response.bodyAsString());
            assertThat(status.getString("benchmark")).isEqualTo("test");
            System.out.println(status.encodePrettily());
            if (status.getString("terminated") != null) {
               JsonArray errors = status.getJsonArray("errors");
               assertThat(errors).isNotNull();
               assertThat(errors.size()).withFailMessage("Found errors: %s", errors).isEqualTo(0);
               assertThat(status.getString("started")).isNotNull();
               JsonArray agents = status.getJsonArray("agents");
               for (int i = 0; i < agents.size(); ++i) {
                  assertThat(agents.getJsonObject(i).getString("status")).isNotEqualTo("STARTING");
               }
               termination.complete();
            } else {
               vertx.setTimer(100, id -> {
                  getStatus(ctx, client, location, termination);
               });
            }
         } catch (Throwable t) {
            ctx.fail(t);
            throw t;
         }
      }));
   }

   private byte[] serialize(Serializable object) {
      try {
         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(object);
         }
         byteArrayOutputStream.flush();
         return byteArrayOutputStream.toByteArray();
      } catch (IOException e) {
         throw new AssertionError(e);
      }
   }

}
