package io.hyperfoil;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.clustering.AgentVerticle;
import io.hyperfoil.clustering.Codecs;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class Hyperfoil {
   static final Logger log = LoggerFactory.getLogger(Controller.class);

   static void clusteredVertx(Handler<Vertx> startedHandler) {
      logJavaVersion();
      log.info("Starting Vert.x...");
      VertxOptions options = new VertxOptions().setClustered(true);
      try {
         String hostName = InetAddress.getLocalHost().getHostName();
         options.setClusterHost(hostName);
      } catch (UnknownHostException e) {
         log.error("Cannot lookup hostname", e);
      }
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

   public static class Agent extends Hyperfoil {
      public static void main(String[] args) {
         clusteredVertx(vertx -> deploy(vertx, AgentVerticle.class));
      }
   }

   public static class Controller extends Hyperfoil {
      public static void main(String[] args) {
         clusteredVertx(vertx -> deploy(vertx, ControllerVerticle.class));
      }
   }

   public static class Standalone extends Hyperfoil {
      public static void main(String[] args) {
         logJavaVersion();
         log.info("Starting non-clustered Vert.x with controller and single agent...");
         Vertx vertx = Vertx.vertx();
         Codecs.register(vertx);
         deploy(vertx, ControllerVerticle.class);
         deploy(vertx, AgentVerticle.class);
      }
   }

   private static void logJavaVersion() {
      log.info("{} {} {} {} ({})",
            System.getProperty("java.vm.vendor", "<unknown VM vendor>"),
            System.getProperty("java.vm.name", "<unknown VM name>"),
            System.getProperty("java.version", "<unknown version>"),
            System.getProperty("java.vm.version", "<unknown VM version>"),
            System.getProperty("java.home", "<unknown Java home>"));
   }
}
