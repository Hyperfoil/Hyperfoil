package io.sailrocket.distributed;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.FutureFactoryImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class Main {
   private static final Logger log = LoggerFactory.getLogger(Main.class);
   private static final boolean clustered = Boolean.parseBoolean(System.getProperty(Properties.CLUSTERED, "true"));

   public static void main(String[] args) {
      log.info("Starting {} Vert.x server...", clustered ? "clustered" : "non-clustered");
      VertxOptions options = new VertxOptions().setClustered(clustered);
      if (clustered) {
         Vertx.clusteredVertx(options, Main::serverStarted);
      } else {
         serverStarted(new FutureFactoryImpl().succeededFuture(Vertx.vertx()));
      }
   }

   private static void serverStarted(AsyncResult<Vertx> result) {
      if (result.failed()) {
         log.error("Cannot start Vert.x server", result.cause());
         return;
      }
      Vertx vertx = result.result();
      log.info("Deploying {}...", AgentControllerVerticle.class.getSimpleName());
      vertx.deployVerticle(AgentControllerVerticle.class, new DeploymentOptions(), event -> {
         if (event.succeeded()) {
            log.info("SailRocket started.");
         } else {
            log.error("Failed to deploy.", event.cause());
            System.exit(1);
         }
      });
   }
}
