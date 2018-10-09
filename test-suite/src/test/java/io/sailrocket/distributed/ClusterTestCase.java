package io.sailrocket.distributed;

import io.sailrocket.test.Benchmark;
import io.sailrocket.test.TestBenchmarks;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.asynchttpclient.Dsl.asyncHttpClient;

@RunWith(VertxUnitRunner.class)
@Category(Benchmark.class)
public class ClusterTestCase {

    public static final String BASE_URL = "http://localhost:8090";
    private Collection<Vertx> servers = new ArrayList<>();

    private static final int CONTROLLERS = 1;
    private static final int AGENTS = 2;

    private final int EXPECTED_COUNT = AGENTS * 5000;

    @Before
    public void before(TestContext ctx) {

        Async initAsync = ctx.async(AGENTS + 2);
        //dummy http server to test against

        standalone().createHttpServer().requestHandler(req -> {
            req.response().end("test");
        }).listen(8080, "localhost", ar -> {
            if (ar.succeeded()) initAsync.countDown();
            else ctx.fail(ar.cause());
        });


        VertxOptions opts = new VertxOptions().setClustered(true);
        JsonObject config = null;

        //configure multi node vert.x cluster
        initiateController(opts, config, ctx, initAsync);

        //create multiple runner nodes
        IntStream.range(0, AGENTS).forEach(id -> initiateRunner(opts, config, ctx, initAsync));
    }

    @After
    public void teardown() {
        servers.forEach(Vertx::close);
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
                  .prepareGet(BASE_URL + "/agents")
                  .execute()
                  .toCompletableFuture()
                  .thenApply(Response::getResponseBody)
                  .thenApply(response -> AGENTS == Integer.parseInt(response))
                  .join()) {
                Thread.sleep(1000);
            }

            // upload benchmark
            asyncHttpClient
                  .preparePost(BASE_URL + "/benchmark")
                  .setHeader(HttpHeaders.CONTENT_TYPE, "application/java-serialized-object")
                  .setBody(serialize(TestBenchmarks.testBenchmark(AGENTS)))
                  .execute()
                  .toCompletableFuture()
                  .thenAccept(response -> {
                      assertThat(response.getStatusCode()).isEqualTo(204);
                      assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo(BASE_URL + "/benchmark/test");
                  })
                  .join();

            // list benchmarks
            asyncHttpClient
                  .prepareGet(BASE_URL + "/benchmark")
                  .execute()
                  .toCompletableFuture()
                  .thenAccept(response -> {
                      assertThat(response.getStatusCode()).isEqualTo(200);
                      assertThat(new JsonArray(response.getResponseBody()).contains("test")).isTrue();
                  })
                  .join();

            //start benchmark running
            String runLocation = asyncHttpClient
                  .prepareGet(BASE_URL + "/benchmark/test/start")
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
        initiateClustered(opts, AgentControllerVerticle.class, new DeploymentOptions().setConfig(config).setWorker(false), ctx, initAsync);
    }

    private void initiateRunner(VertxOptions opts, JsonObject config, TestContext ctx, Async initAsync) {
        initiateClustered(opts, AgentVerticle.class, new DeploymentOptions().setConfig(config).setWorker(true), ctx, initAsync);
    }

    private void initiateClustered(VertxOptions opts, Class<? extends Verticle> verticleClass, DeploymentOptions options, TestContext ctx, Async initAsync) {
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                servers.add(vertx);
                // Codecs can be registered just once per vertx node so we can't register them in verticles
                Codecs.register(vertx);
                vertx.deployVerticle(verticleClass.getName(), options, v -> {
                    if (v.succeeded()) {
                        initAsync.countDown();
                    } else {
                        ctx.fail(v.cause());
                    }
                });
            } else {
                ctx.fail(result.cause());
            }
        });
    }

}
