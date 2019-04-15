package io.hyperfoil.clustering;

import io.hyperfoil.test.Benchmark;
import io.hyperfoil.test.TestBenchmarks;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;

@RunWith(VertxUnitRunner.class)
@Category(Benchmark.class)
public class ClusterTestCase extends BaseClusteredTest {

    private static final String CONTROLLER_URL = "http://localhost:8090";
    private static final int AGENTS = 2;

    private final int EXPECTED_COUNT = AGENTS * 5000;
    private HttpServer httpServer;

    @Before
    public void before(TestContext ctx) {

        //dummy http server to test against

        httpServer = standalone().createHttpServer().requestHandler(req -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();  // TODO: Customise this generated block
            }
            req.response().end("test");
        }).listen(0, "localhost", ctx.asyncAssertSuccess());

        Async initAsync = ctx.async(AGENTS + 1);
        VertxOptions opts = new VertxOptions().setClustered(true);

        //configure multi node vert.x cluster
        initiateController(opts, null, ctx, initAsync);

        //create multiple runner nodes
        IntStream.range(0, AGENTS).forEach(id -> initiateRunner(opts, new JsonObject().put("name", "agent" + id), ctx, initAsync));
    }

    Vertx standalone() {
        Vertx vertx = Vertx.vertx();
        servers.add(vertx);
        return vertx;
    }

    @Test(timeout = 120_000)
    public void startClusteredBenchmarkTest() throws IOException, InterruptedException {
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            //check expected number of nodes are running
            while (!asyncHttpClient
                  .prepareGet(CONTROLLER_URL + "/agents")
                  .execute()
                  .toCompletableFuture()
                  .thenApply(Response::getResponseBody)
                  .thenApply(response -> AGENTS == new JsonArray(response).size())
                  .join()) {
                Thread.sleep(1000);
            }

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
                      assertThat(response.getStatusCode()).isEqualTo(200);
                      String responseBody = response.getResponseBody();
                      JsonObject status = new JsonObject(responseBody);
                      assertThat(status.getString("benchmark")).isEqualTo("test");
                      System.out.println(status.encodePrettily());
                      return status.getString("terminated") != null;
                  }).join()) {
                Thread.sleep(1000);
            }
        }
    }

    private byte[] serialize(Serializable object) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)){
                objectOutputStream.writeObject(object);
            }
            byteArrayOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void initiateController(VertxOptions opts, JsonObject config, TestContext ctx, Async initAsync) {
        initiateClustered(opts, ControllerVerticle.class, new DeploymentOptions().setConfig(config).setWorker(false), ctx, initAsync);
    }

    private void initiateRunner(VertxOptions opts, JsonObject config, TestContext ctx, Async initAsync) {
        initiateClustered(opts, AgentVerticle.class, new DeploymentOptions().setConfig(config).setWorker(true), ctx, initAsync);
    }

}
