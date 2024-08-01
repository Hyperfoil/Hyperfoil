package io.hyperfoil.benchmark.clustering;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

// Note: this test won't run from IDE (probably) as SshDeployer copies just .jar files for the agents;
// since it inspects classpath for .jars it won't copy the class files in the hyperfoil-clustering module.
// It runs from Maven just fine.
@Tag("io.hyperfoil.test.Benchmark")
public class ClusterTestCase extends BaseClusteredTest {
   private static final int AGENTS = 2;

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
            e.printStackTrace(); // TODO: Customise this generated block
         }
         req.response().end("test");
      };
   }

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      super.before(vertx, ctx);
      startController(ctx);
   }

   @Test
   public void startClusteredBenchmarkTest(VertxTestContext ctx) {
      assertTimeout(Duration.ofSeconds(120), () -> {
         WebClientOptions options = new WebClientOptions().setDefaultHost("localhost").setDefaultPort(controllerPort);
         WebClient client = WebClient.create(this.vertx, options.setFollowRedirects(false));
         var termination = ctx.checkpoint();

         // upload benchmark
         Promise<HttpResponse<Buffer>> uploadPromise = Promise.promise();
         client.post("/benchmark")
               .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/java-serialized-object")
               .sendBuffer(Buffer.buffer(serialize(testBenchmark(AGENTS, httpServer.actualPort()))),
                     ctx.succeeding(uploadPromise::complete));

         Promise<HttpResponse<Buffer>> benchmarkPromise = Promise.promise();
         uploadPromise.future().onSuccess(response -> {
            assertEquals(response.statusCode(), 204);
            assertEquals(response.getHeader(HttpHeaders.LOCATION.toString()),
                  "http://localhost:" + controllerPort + "/benchmark/test");
            // list benchmarks
            client.get("/benchmark").send(ctx.succeeding(benchmarkPromise::complete));
         });

         Promise<HttpResponse<Buffer>> startPromise = Promise.promise();
         benchmarkPromise.future().onSuccess(response -> {
            assertEquals(response.statusCode(), 200);
            assertTrue(new JsonArray(response.bodyAsString()).contains("test"));
            //start benchmark running
            client.get("/benchmark/test/start").send(ctx.succeeding(startPromise::complete));
         });

         startPromise.future().onSuccess(response -> {
            assertEquals(response.statusCode(), 202);
            String location = response.getHeader(HttpHeaders.LOCATION.toString());
            getStatus(ctx, client, location, termination);
         });
      });
   }

   private void getStatus(VertxTestContext ctx, WebClient client, String location, Checkpoint termination) {
      client.get(location).send(ctx.succeeding(response -> {
         assertEquals(response.statusCode(), 200);
         try {
            JsonObject status = new JsonObject(response.bodyAsString());
            assertThat(status.getString("benchmark")).isEqualTo("test");
            if (status.getString("terminated") != null) {
               JsonArray errors = status.getJsonArray("errors");
               assertThat(errors).isNotNull();
               assertThat(errors.size()).withFailMessage("Found errors: %s", errors).isEqualTo(0);
               assertThat(status.getString("started")).isNotNull();
               JsonArray agents = status.getJsonArray("agents");
               for (int i = 0; i < agents.size(); ++i) {
                  assertThat(agents.getJsonObject(i).getString("status")).isNotEqualTo("STARTING");
               }
               termination.flag();
            } else {
               vertx.setTimer(100, id -> {
                  getStatus(ctx, client, location, termination);
               });
            }
         } catch (Throwable t) {
            ctx.failNow(t);
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
