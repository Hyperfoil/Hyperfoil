package io.hyperfoil.benchmark.standalone;

import java.util.concurrent.ThreadLocalRandom;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class BaseBenchmarkTestCase {
    protected volatile int count;
    protected long unservedDelay;
    protected double servedRatio = 1.0;
    private Vertx vertx;

    @Before
    public void before(TestContext ctx) {
        count = 0;
        vertx = Vertx.vertx();
        vertx.createHttpServer().requestHandler(req -> {
            count++;
            if (servedRatio >= 1.0 || ThreadLocalRandom.current().nextDouble() < servedRatio) {
                req.response().end();
            } else {
                if (unservedDelay > 0) {
                    vertx.setTimer(unservedDelay, timer -> req.connection().close());
                } else {
                    req.connection().close();
                }
            }
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());
    }

    @After
    public void after(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }
}
