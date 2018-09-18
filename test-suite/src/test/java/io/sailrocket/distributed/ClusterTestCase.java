package io.sailrocket.distributed;

import io.sailrocket.test.Benchmark;
import io.sailrocket.test.TestBenchmarks;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
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
        //check expected number of nodes are running
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            while (!asyncHttpClient
                  .prepareGet("http://localhost:8090/agents")
                  .execute()
                  .toCompletableFuture()
                  .thenApply(Response::getResponseBody)
                  .thenApply(response -> AGENTS == Integer.parseInt(response))
                  .join()) {
                Thread.sleep(1000);
            }

            asyncHttpClient
                  .preparePost("http://localhost:8090/upload")
                  .setBody(serialize(TestBenchmarks.testBenchmark(AGENTS)))
                  .execute()
                  .toCompletableFuture()
                  .thenAccept(response -> {
                      assertThat(response.getStatusCode()).isEqualTo(200);
                  })
                  .join();

            //start benchmark running
            asyncHttpClient
                  .prepareGet("http://localhost:8090/start?benchmark=test")
                  .execute()
                  .toCompletableFuture()
                  .thenAccept(response -> {
                      assertThat(response.getStatusCode()).isEqualTo(202);
                  })
                  .join();

            while (!asyncHttpClient
                  .prepareGet("http://localhost:8090/status")
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
