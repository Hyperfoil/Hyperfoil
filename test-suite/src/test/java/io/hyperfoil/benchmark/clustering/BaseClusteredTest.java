package io.hyperfoil.benchmark.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.After;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

public abstract class BaseClusteredTest extends BaseBenchmarkTest {
   protected List<Vertx> servers = new ArrayList<>();
   protected volatile int controllerPort;

   @After
   public void teardown(TestContext ctx) {
      servers.forEach(vertx -> Hyperfoil.shutdownVertx(vertx).onComplete(ctx.asyncAssertSuccess()));
   }

   protected void startController(TestContext ctx) {
      //configure multi node vert.x cluster
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
      Async initAsync = ctx.async();
      Hyperfoil.clusteredVertx(true).onSuccess(vertx -> {
         servers.add(vertx);
         vertx.deployVerticle(ControllerVerticle.class, new DeploymentOptions())
               .onSuccess(deploymentId -> {
                  Set<Verticle> verticles = ((VertxInternal) vertx).getDeployment(deploymentId).getVerticles();
                  ControllerVerticle controller = (ControllerVerticle) verticles.iterator().next();
                  controllerPort = controller.actualPort();
                  initAsync.countDown();
               }).onFailure(ctx::fail);
      }).onFailure(cause -> ctx.fail("Failed to start clustered Vert.x, see log for details"));
   }
}
