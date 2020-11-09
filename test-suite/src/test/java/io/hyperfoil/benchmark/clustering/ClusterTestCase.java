package io.hyperfoil.benchmark.clustering;

import io.hyperfoil.test.Benchmark;
import io.hyperfoil.test.TestBenchmarks;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.asynchttpclient.AsyncHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;

// Note: this test won't run from IDE (probably) as SshDeployer copies just .jar files for the agents;
// since it inspects classpath for .jars it won't copy the class files in the hyperfoil-clustering module.
// It runs from Maven just fine.
@RunWith(VertxUnitRunner.class)
@Category(Benchmark.class)
public class ClusterTestCase extends BaseClusteredTest {

   private static final String CONTROLLER_URL = "http://localhost:8090";
   private static final int AGENTS = 2;

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
      try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
         // upload benchmark
         asyncHttpClient
               .preparePost(CONTROLLER_URL + "/benchmark")
               .setHeader(HttpHeaders.CONTENT_TYPE, "application/java-serialized-object")
               .setBody(serialize(TestBenchmarks.testBenchmark(AGENTS, httpServer.actualPort())))
               .execute()
               .toCompletableFuture()
               .thenAccept(response -> {
                  assertThat(response.getStatusCode()).isEqualTo(204);
                  assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(CONTROLLER_URL + "/benchmark/test");
               })
               .join();

         // list benchmarks
         asyncHttpClient
               .prepareGet(CONTROLLER_URL + "/benchmark")
               .execute()
               .toCompletableFuture()
               .thenAccept(response -> {
                  assertThat(response.getStatusCode()).isEqualTo(200);
                  assertThat(new JsonArray(response.getResponseBody()).contains("test")).isTrue();
               })
               .join();

         //start benchmark running
         String runLocation = asyncHttpClient
               .prepareGet(CONTROLLER_URL + "/benchmark/test/start")
               .execute()
               .toCompletableFuture()
               .thenApply(response -> {
                  assertThat(response.getStatusCode()).isEqualTo(202);
                  return response.getHeader(HttpHeaders.LOCATION);
               })
               .join();

         while (!asyncHttpClient
               .prepareGet(runLocation)
               .execute()
               .toCompletableFuture()
               .thenApply(response -> {
                  try {
                     assertThat(response.getStatusCode()).isEqualTo(200);
                     String responseBody = response.getResponseBody();
                     JsonObject status = new JsonObject(responseBody);
                     assertThat(status.getString("benchmark")).isEqualTo("test");
                     System.out.println(status.encodePrettily());
                     boolean terminated = status.getString("terminated") != null;
                     if (terminated) {
                        assertThat(status.getString("started")).isNotNull();
                        JsonArray agents = status.getJsonArray("agents");
                        for (int i = 0; i < agents.size(); ++i) {
                           assertThat(agents.getJsonObject(i).getString("status")).isNotEqualTo("STARTING");
                        }
                     }
                     return terminated;
                  } catch (Throwable t) {
                     ctx.fail(t);
                     throw t;
                  }
               }).join()) {
            Thread.sleep(1000);
         }
      }
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
