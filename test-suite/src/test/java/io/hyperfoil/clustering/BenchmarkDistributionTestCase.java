package io.hyperfoil.clustering;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;

@Ignore
@RunWith(VertxUnitRunner.class)
public class BenchmarkDistributionTestCase {

    private Vertx vertServer;
    private Vertx vertCluster;

    @Before
    public void before(TestContext ctx) {
        vertServer = Vertx.vertx();
        vertServer.createHttpServer().requestHandler(req -> {
            req.response().end();
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());
    }



}
