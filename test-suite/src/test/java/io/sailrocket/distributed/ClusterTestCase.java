package io.sailrocket.distributed;

import io.sailrocket.core.client.HttpClient;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.HttpMethod;
import io.sailrocket.core.client.vertx.VertxHttpClient;
import io.sailrocket.core.client.vertx.VertxHttpClientBuilder;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(VertxUnitRunner.class)
public class ClusterTestCase {

    public static final int MASTER_PORT = 8090;

    private Vertx vertServer;
    private Vertx vertCluster;

    private final CountDownLatch responseLatch = new CountDownLatch(1);
    private final CountDownLatch clusterLatch = new CountDownLatch(4);

    @Before
    public void before(TestContext ctx) {

        //dummy http server to test against
        vertServer = Vertx.vertx();
        vertServer.createHttpServer().requestHandler(req -> {
            req.response().end();
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());


        VertxOptions opts = new VertxOptions().setClustered(true);
        JsonObject config = null;

        //vert.x cluster
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                vertx.deployVerticle(AgentControllerVerticle.class.getName(), new DeploymentOptions().setConfig(config).setWorker(false), v -> {clusterLatch.countDown();});
                clusterLatch.countDown();
            } else {
                System.out.println("Clusterin failed");
                throw new RuntimeException(result.cause());
            }
        });
        Vertx.clusteredVertx(opts, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                vertx.deployVerticle(RunnerVerticle.class.getName(), new DeploymentOptions().setConfig(config).setWorker(true), v -> {clusterLatch.countDown();});
                clusterLatch.countDown();
            } else {
                System.out.println("Clusterin failed");
                throw new RuntimeException(result.cause());
            }
        });


    }

    @After
    public void teardown() {
        vertServer.close();
    }

    @Test
    public void startClusteredBenchmarkTest() {
        VertxHttpClientBuilder clientBuilder = (VertxHttpClientBuilder) HttpClientProvider.vertx.builder()
                .threads(1)
                .ssl(false)
                .port(MASTER_PORT)
                .host("localhost")
                .size(1)
                .concurrency(1)
                .protocol(HttpVersion.HTTP_1_1);

        HttpClient httpClient = new VertxHttpClient(clientBuilder);

        try {
            clusterLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Sending Request");

        httpClient
                .request(HttpMethod.GET, "/start")
                .resetHandler(v -> {
                    System.out.println("reset from controller received");
                    responseLatch.countDown();
                })
                .endHandler(v -> {
                    System.out.println("response from controller received");
                    responseLatch.countDown();
                })
                .end();

        try {
            responseLatch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
