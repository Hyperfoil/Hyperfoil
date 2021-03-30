package io.hyperfoil.clustering;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.Hyperfoil;
import io.hyperfoil.clustering.messages.AuxiliaryHello;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.NodeListener;

public class BaseAuxiliaryVerticle extends AbstractVerticle implements NodeListener {
   protected final Logger log = LogManager.getLogger(getClass());
   private String nodeId = "unknown";
   private String controllerNodeId;
   private int registrationAttempt = 0;

   @Override
   public void start() {
      if (vertx.isClustered()) {
         if (vertx instanceof VertxInternal) {
            VertxInternal internal = (VertxInternal) this.vertx;
            if (internal.getClusterManager().getNodes().size() < 2) {
               log.info("Did not cluster with Hyperfoil Controller, shutting down.");
               Hyperfoil.shutdownVertx(vertx);
               return;
            }
            nodeId = internal.getClusterManager().getNodeId();
            internal.getClusterManager().nodeListener(this);
         }
      }
      vertx.setPeriodic(1000, timerId -> {
         vertx.eventBus().request(Feeds.DISCOVERY, new AuxiliaryHello("CE Receiver", nodeId, deploymentID()), response -> {
            if (response.succeeded()) {
               log.info("Successfully registered at controller {}!", response.result().body());
               vertx.cancelTimer(timerId);
               controllerNodeId = (String) response.result().body();
               onRegistered();
            } else {
               if (registrationAttempt++ < 10) {
                  log.info("Auxiliary registration failed (attempt {})", registrationAttempt);
                  if (registrationAttempt == 10) {
                     log.info("Suspending registration failure logs.");
                  }
               }
            }
         });
      });
   }

   @Override
   public void nodeAdded(String nodeID) {
   }

   @Override
   public void nodeLeft(String nodeID) {
      if (Objects.equals(nodeID, controllerNodeId)) {
         // Since we assume running in Openshift/Kubernetes we will let the container restart.
         // Otherwise we would have to start a new cluster (that means a new Vert.x) which
         // is more complicated.
         log.info("Controller left the cluster, shutting down...");
         Hyperfoil.shutdownVertx(vertx);
      }
   }

   public void onRegistered() {
   }
}
