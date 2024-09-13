package io.hyperfoil.benchmark;

import java.io.File;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public abstract class BaseBenchmarkTest {
   protected Vertx vertx;
   protected HttpServer httpServer;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      this.vertx = vertx;
      setupHttpServer(ctx, getRequestHandler());
   }

   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> req.response().end();
   }

   private void setupHttpServer(VertxTestContext ctx, Handler<HttpServerRequest> handler) {
      httpServer = vertx.createHttpServer().requestHandler(handler).listen(0, "localhost", ctx.succeedingThenComplete());
   }

   protected String getBenchmarkPath(String name) {
      URL resource = getClass().getClassLoader().getResource(name);
      if (resource == null) {
         throw new AssertionError("Resource named: " + name + " not found");
      }
      File benchmark = new File(resource.getFile());
      return benchmark.getAbsolutePath();
   }
}
