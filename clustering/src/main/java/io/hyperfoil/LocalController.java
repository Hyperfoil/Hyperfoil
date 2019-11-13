package io.hyperfoil;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.clustering.Codecs;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.clustering.Properties;
import io.hyperfoil.internal.Controller;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class LocalController implements Controller {
   private final Vertx vertx;
   private final String host;
   private final int port;

   public LocalController(Vertx vertx, String host, int port) {
      this.vertx = vertx;
      this.host = host;
      this.port = port;
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
      vertx.close(result -> {
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
      public Controller start(Path rootDir) {
         if (rootDir != null) {
            System.setProperty(Properties.ROOT_DIR, rootDir.toFile().getAbsolutePath());
         }
         System.setProperty(Properties.CONTROLLER_HOST, "127.0.0.1");
         System.setProperty(Properties.CONTROLLER_PORT, "0");
         Vertx vertx = Vertx.vertx();
         Codecs.register(vertx);
         CompletableFuture<Integer> completion = new CompletableFuture<>();
         ControllerVerticle controller = new ControllerVerticle();
         vertx.deployVerticle(controller, new DeploymentOptions(), event -> {
            if (event.succeeded()) {
               completion.complete(controller.actualPort());
            } else {
               completion.completeExceptionally(event.cause());
            }
         });
         return new LocalController(vertx, "127.0.0.1", completion.join());
      }
   }
}
