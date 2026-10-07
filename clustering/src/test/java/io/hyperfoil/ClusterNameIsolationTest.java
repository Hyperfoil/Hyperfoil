package io.hyperfoil;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.internal.Properties;
import io.vertx.core.Vertx;

public class ClusterNameIsolationTest {
   private static final String BIND_ADDRESS = "127.0.0.1";

   private final List<Vertx> started = new ArrayList<>();

   @AfterEach
   public void cleanup() throws Exception {
      for (Vertx vertx : started) {
         vertx.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
      }
      started.clear();
      System.clearProperty(Properties.CLUSTER_NAME);
      System.clearProperty(Properties.CLUSTER_JGROUPS_STACK);
      System.clearProperty(Properties.CONTROLLER_CLUSTER_IP);
      System.clearProperty(Properties.CONTROLLER_CLUSTER_PORT);
      System.clearProperty("jgroups.tcp.address");
   }

   @Test
   public void testAgentIgnoresControllerOfAnotherCluster() throws Exception {
      Vertx controller = startController("hyperfoil-run-a");
      Vertx agent = startAgentPointedAtController("hyperfoil-run-b");
      assertFalse(reachesEachOther(controller, agent),
            "Agent of run-b joined the controller of run-a");
   }

   @Test
   public void testAgentJoinsControllerOfItsOwnCluster() throws Exception {
      Vertx controller = startController("hyperfoil-run-a");
      Vertx agent = startAgentPointedAtController("hyperfoil-run-a");
      assertTrue(reachesEachOther(controller, agent),
            "Agent did not join the controller of its own cluster");
   }

   private Vertx startController(String clusterName) throws Exception {
      System.setProperty(Properties.CLUSTER_NAME, clusterName);
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, BIND_ADDRESS);
      return start(true);
   }

   private Vertx startAgentPointedAtController(String clusterName) throws Exception {
      System.setProperty(Properties.CLUSTER_NAME, clusterName);
      return start(false);
   }

   private Vertx start(boolean isController) throws Exception {
      Vertx vertx = Hyperfoil.clusteredVertx(isController).toCompletionStage().toCompletableFuture().get(60, SECONDS);
      started.add(vertx);
      return vertx;
   }

   private static boolean reachesEachOther(Vertx from, Vertx to) throws Exception {
      String address = "cluster-name-isolation-test";
      CountDownLatch received = new CountDownLatch(1);
      to.eventBus().<String> consumer(address, message -> received.countDown())
            .completion().toCompletionStage().toCompletableFuture().get(30, SECONDS);
      for (int i = 0; i < 20; ++i) {
         from.eventBus().publish(address, "ping");
         if (received.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            return true;
         }
      }
      return false;
   }
}
