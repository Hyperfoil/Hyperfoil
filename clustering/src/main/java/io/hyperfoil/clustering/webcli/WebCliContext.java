package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;

class WebCliContext extends HyperfoilCliContext {
   CountDownLatch latch;
   StringBuilder editBenchmark;
   final ServerWebSocket webSocket;

   public WebCliContext(Vertx vertx, ServerWebSocket webSocket) {
      super(vertx, true);
      this.webSocket = webSocket;
   }

   @Override
   public String interruptKey() {
      return "Escape";
   }

   @Override
   public synchronized Pager createPager(String pager) {
      latch = new CountDownLatch(1);
      return new WebPager();
   }
}
