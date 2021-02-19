package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.vertx.core.Vertx;

class WebCliContext extends HyperfoilCliContext {
   CountDownLatch latch;
   StringBuilder editBenchmark;

   public WebCliContext(Vertx vertx) {
      super(vertx, true);
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
