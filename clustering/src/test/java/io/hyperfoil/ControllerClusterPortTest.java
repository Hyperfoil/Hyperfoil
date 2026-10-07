package io.hyperfoil;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.internal.Properties;
import io.vertx.core.Vertx;

/**
 * The controller publishes {@link Properties#CONTROLLER_CLUSTER_PORT} for its agents to connect to.
 * JGroups binds anywhere in [bind_port, bind_port + TP.port_range] (default range 10), so whenever
 * the configured port is taken - another controller, or any unrelated process - the controller ends
 * up listening somewhere else and must publish the port it really bound to.
 */
public class ControllerClusterPortTest {
   private static final String BIND_ADDRESS = "127.0.0.1";

   private ServerSocket occupiedPort;
   private Vertx vertx;

   @AfterEach
   public void cleanup() throws Exception {
      if (vertx != null) {
         vertx.close().toCompletionStage().toCompletableFuture().get(30, SECONDS);
      }
      if (occupiedPort != null) {
         occupiedPort.close();
      }
      System.clearProperty(Properties.CONTROLLER_CLUSTER_IP);
      System.clearProperty(Properties.CONTROLLER_CLUSTER_PORT);
      System.clearProperty("jgroups.tcp.address");
      System.clearProperty("jgroups.bind.address");
      System.clearProperty("jgroups.bind.port");
   }

   @Test
   public void testPublishesThePortItActuallyBoundTo() throws Exception {
      // Hold the port the controller is configured to use, forcing JGroups onto the next one.
      occupiedPort = new ServerSocket();
      occupiedPort.bind(new InetSocketAddress(InetAddress.getByName(BIND_ADDRESS), 0));
      int configuredPort = occupiedPort.getLocalPort();

      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, BIND_ADDRESS);
      System.setProperty("jgroups.bind.address", BIND_ADDRESS);
      System.setProperty("jgroups.bind.port", String.valueOf(configuredPort));

      vertx = Hyperfoil.clusteredVertx(true).toCompletionStage().toCompletableFuture().get(60, SECONDS);

      String published = Properties.get(Properties.CONTROLLER_CLUSTER_PORT, null);
      assertNotNull(published, "Controller did not publish its clustering port");
      int publishedPort = Integer.parseInt(published);

      // Before the fix this was the configured port, which nothing is listening on.
      assertNotEquals(configuredPort, publishedPort,
            "Controller published the configured port even though it was taken");
      assertTrue(isListening(publishedPort), "Nothing is listening on the published port " + publishedPort);
   }

   private static boolean isListening(int port) {
      try (ServerSocket probe = new ServerSocket()) {
         probe.bind(new InetSocketAddress(InetAddress.getByName(BIND_ADDRESS), port));
         return false;
      } catch (IOException e) {
         return true;
      }
   }
}
