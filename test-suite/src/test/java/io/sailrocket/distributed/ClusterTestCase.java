package io.sailrocket.distributed;

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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.IntStream;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@RunWith(VertxUnitRunner.class)
//@Category(Benchmark.class)
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
    public void startClusteredBenchmarkTest(TestContext ctx) throws IOException, InterruptedException {
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
                        ctx.async().complete();
                    })
                    .join();
        }
    }

    private void initiateController(VertxOptions opts, JsonObject config, TestContext ctx, Async initAsync) {
        initiateClustered(opts, AgentControllerVerticle.class, new DeploymentOptions().setConfig(config).setWorker(false), ctx, initAsync);
    }

    private void initiateRunner(VertxOptions opts, JsonObject config, TestContext ctx, Async initAsync) {
        initiateClustered(opts, RunnerVerticle.class, new DeploymentOptions().setConfig(config).setWorker(true), ctx, initAsync);
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
