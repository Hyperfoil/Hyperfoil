package io.hyperfoil.benchmark.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

public abstract class BaseClusteredTest extends BaseBenchmarkTest {
   protected static final Logger log = LogManager.getLogger(BaseClusteredTest.class);
   protected List<Vertx> servers = new ArrayList<>();
   protected volatile int controllerPort;

   @AfterEach
   public void tearDown(VertxTestContext ctx) {
      servers.forEach(vertx -> Hyperfoil.shutdownVertx(vertx).onComplete(ctx.succeedingThenComplete()));
   }

   protected void startController(VertxTestContext ctx) {
      log.info("=== Starting BaseClusteredTest setup ===");
      // Some clustered tests time out in GitHub Actions because the agents don't cluster soon enough.
      System.setProperty("jgroups.join_timeout", "15000");
      //configure multi node vert.x cluster
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");

      // Use a high port range to avoid conflicts with common services
      // JGroups with TCPPING doesn't support bind_port=0, so we use a specific port
      // Using port range 17800-17899 which is less likely to be in use
      String testPort = String.valueOf(17800 + (int) (Math.random() * 100));
      System.setProperty(Properties.CONTROLLER_CLUSTER_PORT, testPort);
      System.setProperty("jgroups.bind.port", testPort);
      System.setProperty("jgroups.tcp.port", testPort);

      log.info("System properties set:");
      log.info("  jgroups.join_timeout: {}", System.getProperty("jgroups.join_timeout"));
      log.info("  jgroups.bind.port: {}", System.getProperty("jgroups.bind.port"));
      log.info("  jgroups.tcp.port: {}", System.getProperty("jgroups.tcp.port"));
      log.info("  {}: {}", Properties.CONTROLLER_HOST, System.getProperty(Properties.CONTROLLER_HOST));
      log.info("  {}: {}", Properties.CONTROLLER_PORT, System.getProperty(Properties.CONTROLLER_PORT));
      log.info("  {}: {}", Properties.CONTROLLER_CLUSTER_IP, System.getProperty(Properties.CONTROLLER_CLUSTER_IP));
      log.info("  {}: {}", Properties.CONTROLLER_CLUSTER_PORT, System.getProperty(Properties.CONTROLLER_CLUSTER_PORT));

      // latch used to ensure we wait for controller startup before starting the test
      AtomicReference<ControllerVerticle> controllerRef = new AtomicReference<>();
      var countDownLatch = new CountDownLatch(1);
      log.info("Creating clustered Vert.x instance...");
      Hyperfoil.clusteredVertx(true).onSuccess(vertx -> {
         log.info("Clustered Vert.x created successfully");
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
               }).onFailure(cause -> {
                  log.error("Failed to deploy ControllerVerticle", cause);
                  ctx.failNow(cause);
               });
      }).onFailure(cause -> {
         log.error("Failed to start clustered Vert.x", cause);
         Throwable rootCause = cause;
         int depth = 0;
         while (rootCause.getCause() != null && depth < 10) {
            rootCause = rootCause.getCause();
            log.error("  Caused by [{}]: {} - {}", depth, rootCause.getClass().getName(), rootCause.getMessage());
            depth++;
         }
         ctx.failNow("Failed to start clustered Vert.x, see log for details");
      });

      try {
         if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
            ctx.failNow("Expected 0 latch, controller did not start in time");
         }
      } catch (InterruptedException e) {
         ctx.failNow(e);
      }
   }
}
