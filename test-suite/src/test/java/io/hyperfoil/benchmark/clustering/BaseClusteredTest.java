package io.hyperfoil.benchmark.clustering;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

public abstract class BaseClusteredTest extends BaseBenchmarkTest {
   protected List<Vertx> servers = new ArrayList<>();

   @After
   public void teardown(TestContext ctx) {
      servers.forEach(vertx -> Hyperfoil.shutdownVertx(vertx, ctx.asyncAssertSuccess()));
   }

   protected void startController(TestContext ctx) {
      VertxOptions opts = new VertxOptions();
      opts.getEventBusOptions().setClustered(true);

      //configure multi node vert.x cluster
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
      Async initAsync = ctx.async();
      Hyperfoil.clusteredVertx(true, vertx -> {
         servers.add(vertx);
         vertx.deployVerticle(ControllerVerticle.class, new DeploymentOptions(), result -> {
            if (result.succeeded()) {
               initAsync.countDown();
            } else {
               ctx.fail(result.cause());
            }
         });
      }, () -> ctx.fail("Failed to start clustered Vert.x, see log for details"));
   }
}
