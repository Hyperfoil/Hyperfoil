package io.sailrocket.benchmark.standalone;

import io.sailrocket.core.client.HttpClientProvider;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class BaseBenchmarkTestCase {
    protected volatile int count;
    private Vertx vertx;
    protected HttpClientProvider provider;

    @Before
    public void before(TestContext ctx) {
        count = 0;
        vertx = Vertx.vertx();
        vertx.createHttpServer().requestHandler(req -> {
            count++;
            req.response().end();
        }).listen(8080, "localhost", ctx.asyncAssertSuccess());
    }

    @After
    public void after(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }
}
