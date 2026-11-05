package io.hyperfoil;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.clustering.Codecs;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.internal.Controller;
import io.hyperfoil.internal.Properties;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class LocalController implements Controller {
   private static final Logger log = LoggerFactory.getLogger(LocalController.class);

   private final Vertx vertx;
   private final String host;
   private final int port;
   private final boolean clustered;

   public LocalController(Vertx vertx, String host, int port, boolean clustered) {
      this.vertx = vertx;
      this.host = host;
      this.port = port;
      this.clustered = clustered;
   }

   @Override
   public String host() {
      return host;
   }

   @Override
   public int port() {
      return port;
   }

   @Override
   public void stop() {
      CompletableFuture<Void> stopFuture = new CompletableFuture<>();
      Future<Void> f = clustered ? Hyperfoil.shutdownVertx(vertx) : vertx.close();
      f.onComplete(result -> {
         if (result.succeeded()) {
            stopFuture.complete(null);
         } else {
            stopFuture.completeExceptionally(result.cause());
         }
      });
      stopFuture.join();
   }

   @MetaInfServices(Controller.Factory.class)
   public static class Factory implements Controller.Factory {
      @Override
      public Controller start(Path rootDir, boolean clustered) {
         if (rootDir != null) {
            // TODO: setting property could break test suite but it's that easy to override all uses of Controller.ROOT_DIR
            System.setProperty(Properties.ROOT_DIR, rootDir.toFile().getAbsolutePath());
         } else {
            rootDir = Controller.DEFAULT_ROOT_DIR;
         }
         if (clustered) {
            ensureUniqueClusterName();
         }
         Vertx vertx = resolveVertex(clustered).toCompletableFuture().join();
         String host = resolveLocalHostIp(clustered);
         JsonObject config = new JsonObject();
         config.put(Properties.CONTROLLER_LOG, rootDir.resolve("hyperfoil.local.log").toFile().getAbsolutePath());
         config.put(Properties.CONTROLLER_HOST, host);
         config.put(Properties.CONTROLLER_PORT, 0);

         if (!clustered)
            Codecs.register(vertx);

         Hyperfoil.ensureNettyResourceLeakDetection();
         CompletableFuture<Integer> completion = new CompletableFuture<>();
         ControllerVerticle controller = new ControllerVerticle();
         vertx.deployVerticle(controller, new DeploymentOptions().setConfig(config)).onComplete(event -> {
            if (event.succeeded()) {
               completion.complete(controller.actualPort());
            } else {
               completion.completeExceptionally(event.cause());
            }
         });
         return new LocalController(vertx, host, completion.join(), clustered);
      }

      /**
       * Generates a random JGroups cluster name for this run unless the user pinned one explicitly.
       * Concurrent local controllers would otherwise all join the default "ISPN" cluster and see each
       * other's agents. The property is propagated to deployed agents by the deployers.
       */
      private void ensureUniqueClusterName() {
         if (System.getProperty(Properties.CLUSTER_NAME) == null) {
            String clusterName = "hyperfoil-" + UUID.randomUUID();
            System.setProperty(Properties.CLUSTER_NAME, clusterName);
            log.info("Using generated cluster name {}", clusterName);
         }
      }

      private CompletionStage<Vertx> resolveVertex(boolean clustered) {
         if (!clustered)
            return CompletableFuture.completedFuture(Vertx.vertx());

         return Hyperfoil.clusteredVertx(true).toCompletionStage();
      }

      private String resolveLocalHostIp(boolean clustered) {
         return clustered
               ? System.getProperty(Properties.CONTROLLER_CLUSTER_IP, "127.0.0.1")
               : "127.0.0.1";
      }
   }
}
