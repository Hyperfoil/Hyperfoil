package io.sailrocket.distributed;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@RunWith(VertxUnitRunner.class)
public class ClusterTestCase {

    private Vertx vertServer;

    private static final int CONTROLLERS = 1;
    private static final int AGENTS = 2;

    private final int EXPECTED_COUNT = AGENTS * 5000;

    private final CountDownLatch responseLatch = new CountDownLatch(1);
    private final CountDownLatch clusterLatch = new CountDownLatch((CONTROLLERS + AGENTS) * 2);

    @Before
    public void before(TestContext ctx) {

        //dummy http server to test against
        vertServer = Vertx.vertx();
        vertServer.createHttpServer().requestHandler(req -> {
            req.response().end("test");
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());


        VertxOptions opts = new VertxOptions().setClustered(true);
        JsonObject config = null;

        //configure multi node vert.x cluster
        initiateController(opts, config);

        //create multiple runner nodes
        IntStream.range(0, AGENTS).forEach(id -> initiateRunner(opts, config));

    }

    @After
    public void teardown() {
        vertServer.close();
    }

    @Test
    public void startClusteredBenchmarkTest() throws IOException, InterruptedException {

        //wait for cluster to bootstrap
        if (!clusterLatch.await(30, TimeUnit.SECONDS)) {
            Assert.fail("cluster failed to start in 30s");
        }

        //check expected number of nodes are running
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            asyncHttpClient
                    .prepareGet("http://localhost:8090/agents")
                    .execute()
                    .toCompletableFuture()
                    .thenApply(Response::getResponseBody)
                    .thenAccept(response -> Assert.assertEquals(AGENTS + CONTROLLERS, Integer.parseInt(response)))
                    .join();
        }

        //start benchmark running
        try (AsyncHttpClient asyncHttpClient = asyncHttpClient()) {
            asyncHttpClient
                    .prepareGet("http://localhost:8090/start")
                    .execute()
                    .toCompletableFuture()
                    .thenApply(Response::getResponseBody)
                    .thenAccept(body -> {
                        System.out.println(body);
                        responseLatch.countDown();
                    })
                    .join();
        }

        if (!responseLatch.await(2, TimeUnit.MINUTES)) {
            Assert.fail("Benchmark didn't complete within 2 minutes");
        }

    }


    private void initiateController(VertxOptions opts, JsonObject config) {
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                vertx.deployVerticle(AgentControllerVerticle.class.getName(), new DeploymentOptions().setConfig(config).setWorker(false), v -> {
                    clusterLatch.countDown();
                });
                clusterLatch.countDown();
            } else {
                throw new RuntimeException(result.cause());
            }
        });
    }

    private void initiateRunner(VertxOptions opts, JsonObject config) {
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                vertx.deployVerticle(RunnerVerticle.class.getName(), new DeploymentOptions().setConfig(config).setWorker(true), v -> {
                    clusterLatch.countDown();
                });
                clusterLatch.countDown();
            } else {
                throw new RuntimeException(result.cause());
            }
        });
    }

}
