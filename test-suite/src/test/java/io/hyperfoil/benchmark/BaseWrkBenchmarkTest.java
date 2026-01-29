package io.hyperfoil.benchmark;

import java.util.concurrent.ThreadLocalRandom;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;

/**
 * Base test class providing standardized HTTP endpoints for wrk and wrk2 benchmark testing.
 */
public abstract class BaseWrkBenchmarkTest extends BaseBenchmarkTest {

   private final long unservedDelay = 2000;
   private final double servedRatio = 0.9;

   @Override
   protected final Handler<HttpServerRequest> getRequestHandler() {
      Router router = Router.router(vertx);
      router.route("/10s").handler(ctx -> {
         ctx.vertx().setTimer(10_000, id -> {
            ctx.response().end("10s");
         });
      });
      router.route("/unpredictable").handler(ctx -> {
         if (servedRatio >= 1.0 || ThreadLocalRandom.current().nextDouble() < servedRatio) {
            ctx.response().end();
         } else {
            if (unservedDelay > 0) {
               vertx.setTimer(unservedDelay, timer -> ctx.request().connection().close());
            } else {
               ctx.request().connection().close();
            }
         }
      });
      router.route("/highway").handler(ctx -> {
         ctx.response().end("highway");
      });
      router.route("/1s").handler(ctx -> {
         ctx.vertx().setTimer(1_000, id -> {
            ctx.response().end("1s");
         });
      });
      return router;
   }
}
