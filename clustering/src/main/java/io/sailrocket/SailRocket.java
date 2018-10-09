package io.sailrocket;

import io.sailrocket.clustering.AgentControllerVerticle;
import io.sailrocket.clustering.AgentVerticle;
import io.sailrocket.clustering.Codecs;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class SailRocket {
   static final Logger log = LoggerFactory.getLogger(Controller.class);

   static void clusteredVertx(Handler<Vertx> startedHandler) {
      log.info("Starting Vert.x...");
      VertxOptions options = new VertxOptions().setClustered(true);
      Vertx.clusteredVertx(options, result -> {
         if (result.failed()) {
            log.error("Cannot start Vert.x", result.cause());
            System.exit(1);
         }
         Vertx vertx = result.result();
         Codecs.register(vertx);
         startedHandler.handle(vertx);
      });
   }

   static void deploy(Vertx vertx, Class<? extends Verticle> verticleClass) {
      log.info("Deploying {}...", verticleClass.getSimpleName());
      vertx.deployVerticle(verticleClass, new DeploymentOptions(), event -> {
         if (event.succeeded()) {
            log.info("{} deployed.", verticleClass.getSimpleName());
         } else {
            log.error(event.cause(), "Failed to deploy {}.", verticleClass.getSimpleName());
            System.exit(1);
         }
      });
   }

   public static class Agent extends SailRocket {
      public static void main(String[] args) {
         clusteredVertx(vertx -> deploy(vertx, AgentVerticle.class));
      }
   }

   public static class Controller extends SailRocket {
      public static void main(String[] args) {
         clusteredVertx(vertx -> deploy(vertx, AgentControllerVerticle.class));
      }
   }

   public static class Standalone extends SailRocket {
      public static void main(String[] args) {
         log.info("Starting non-clustered Vert.x with controller and single agent...");
         Vertx vertx = Vertx.vertx();
         deploy(vertx, AgentControllerVerticle.class);
         deploy(vertx, AgentVerticle.class);
      }
   }
}
