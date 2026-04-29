package io.hyperfoil.benchmark.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

public abstract class BaseClusteredTest extends BaseBenchmarkTest {
   protected List<Vertx> servers = new ArrayList<>();
   protected volatile int controllerPort;

   @AfterEach
   public void tearDown(VertxTestContext ctx) {
      servers.forEach(vertx -> Hyperfoil.shutdownVertx(vertx).onComplete(ctx.succeedingThenComplete()));
   }

   protected void startController(VertxTestContext ctx) {
      // Some clustered tests time out in GitHub Actions because the agents don't cluster soon enough.
      System.setProperty("jgroups.join_timeout", "15000");
      //configure multi node vert.x cluster
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");

      // latch used to ensure we wait for controller startup before starting the test
      AtomicReference<ControllerVerticle> controllerRef = new AtomicReference<>();
      var countDownLatch = new CountDownLatch(1);
      Hyperfoil.clusteredVertx(true).onSuccess(vertx -> {
         servers.add(vertx);
         vertx.deployVerticle(() -> {
            ControllerVerticle verticle = new ControllerVerticle();
            controllerRef.set(verticle);
            return verticle;
         }, new DeploymentOptions())
               .onSuccess(deploymentId -> {
                  // 3. Retrieve the port from your captured instance
                  controllerPort = controllerRef.get().actualPort();
                  countDownLatch.countDown();
                  ctx.completeNow();
               }).onFailure(ctx::failNow);
      }).onFailure(cause -> ctx.failNow("Failed to start clustered Vert.x, see log for details"));

      try {
         if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
            ctx.failNow("Expected 0 latch, controller did not start in time");
         }
      } catch (InterruptedException e) {
         ctx.failNow(e);
      }
   }
}
