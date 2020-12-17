package io.hyperfoil.benchmark.standalone;

import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.cli.commands.Wrk2;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.hyperfoil.test.Benchmark;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

@Category(Benchmark.class)
public class WrkTest extends BaseBenchmarkTest {
   protected long unservedDelay = 2000;
   protected double servedRatio = 0.9;

   public WrkTest() {
   }

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return this::serveOrClose;
   }

   private void serveOrClose(HttpServerRequest req) {
      if (servedRatio >= 1.0 || ThreadLocalRandom.current().nextDouble() < servedRatio) {
         req.response().end();
      } else {
         if (unservedDelay > 0) {
            vertx.setTimer(unservedDelay, timer -> req.connection().close());
         } else {
            req.connection().close();
         }
      }
   }

   @Ignore
   @Test
   public void testWrk() {
      Wrk.main(new String[]{ "-c", "10", "-d", "5s", "--latency", "--timeout", "1s", "localhost:" + httpServer.actualPort() + "/foo/bar" });
   }

   @Ignore
   @Test
   public void testWrk2() {
      Wrk2.main(new String[]{ "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "localhost:" + httpServer.actualPort() + "/foo/bar" });
   }
}
