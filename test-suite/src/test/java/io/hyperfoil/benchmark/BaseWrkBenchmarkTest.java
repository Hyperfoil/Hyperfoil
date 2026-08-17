package io.hyperfoil.benchmark;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;

/**
 * Base test class providing standardized HTTP endpoints for wrk and wrk2 benchmark testing.
 */
public abstract class BaseWrkBenchmarkTest extends BaseBenchmarkTest {

   private final long unservedDelay = 100;
   private final double servedRatio = 0.9;

   protected AtomicInteger localStatsReceivedRequests = new AtomicInteger();
   protected AtomicInteger localStatsReceivedResponses = new AtomicInteger();

   @Override
   protected final Handler<HttpServerRequest> getRequestHandler() {
      Router router = Router.router(vertx);

      // global interceptor
      router.route().handler(ctx -> {
         localStatsReceivedRequests.getAndIncrement();
         // callback
         ctx.response().bodyEndHandler(v -> localStatsReceivedResponses.getAndIncrement());
         // forward
         ctx.next();
      });

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
      router.route("/500ms").handler(ctx -> {
         ctx.vertx().setTimer(500, id -> {
            ctx.response().end("500ms!");
         });
      });
      router.route("/1s").handler(ctx -> {
         ctx.vertx().setTimer(1_000, id -> {
            ctx.response().end("1s");
         });
      });
      router.route("/50ms").handler(ctx -> {
         ctx.vertx().setTimer(50, id -> {
            ctx.response().end("50ms!");
         });
      });
      return router;
   }
}
