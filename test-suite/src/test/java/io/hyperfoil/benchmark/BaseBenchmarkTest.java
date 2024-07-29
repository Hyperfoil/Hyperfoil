package io.hyperfoil.benchmark;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public abstract class BaseBenchmarkTest {
   protected Vertx vertx;
   protected HttpServer httpServer;

   @Before
   public void before(TestContext ctx) {
      vertx = getVertx();
      setupHttpServer(ctx, getRequestHandler());
   }

   protected Vertx getVertx() {
      return Vertx.vertx();
   }

   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> req.response().end();
   }

   private void setupHttpServer(TestContext ctx, Handler<HttpServerRequest> handler) {
      httpServer = vertx.createHttpServer().requestHandler(handler).listen(0, "localhost", ctx.asyncAssertSuccess());
   }

   @After
   public void after(TestContext ctx) {
      vertx.close(ctx.asyncAssertSuccess());
   }
}
