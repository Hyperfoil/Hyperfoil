package io.hyperfoil;

import static io.hyperfoil.internal.Properties.CLUSTER_JGROUPS_STACK;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.FormattedMessage;
import org.jgroups.JChannel;
import org.jgroups.PhysicalAddress;
import org.jgroups.protocols.TP;
import org.jgroups.stack.IpAddress;

import io.hyperfoil.api.Version;
import io.hyperfoil.clustering.AgentVerticle;
import io.hyperfoil.clustering.Codecs;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.clustering.jgroups.JGroupsClusterManager;
import io.hyperfoil.internal.Properties;
import io.netty.util.ResourceLeakDetector;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Hyperfoil {
   static final Logger log = LogManager.getLogger(Hyperfoil.class);
   private static final Set<String> LOCALHOST_IPS = new HashSet<>(Arrays.asList("127.0.0.1", "::1", "[::1]"));

   public static Future<Vertx> clusteredVertx(boolean isController) {
      logVersion();
      Thread.setDefaultUncaughtExceptionHandler(Hyperfoil::defaultUncaughtExceptionHandler);
      log.info("Starting Vert.x...");
      VertxOptions options = new VertxOptions();
      try {
         String clusterIp = Properties.get(Properties.CONTROLLER_CLUSTER_IP, null);
         InetAddress address;
         if (isController) {
            if (clusterIp == null) {
               address = InetAddress.getLocalHost();
            } else {
               address = InetAddress.getByName(clusterIp);
            }
         } else {
            if (clusterIp == null) {
               return Future.failedFuture("Controller clustering IP was not set on agent/auxiliary node.");
            }
            InetAddress bestMatch = getAddressWithBestMatch(InetAddress.getByName(clusterIp));
            if (bestMatch != null) {
               address = bestMatch;
            } else {
               address = InetAddress.getLocalHost();
               log.warn("No match found between controller IP ({}) and local addresses, using address {}", clusterIp, address);
            }
         }
         String hostName = address.getHostName();
         String hostAddress = address.getHostAddress();
         log.info("Using host name {}/{}", hostName, hostAddress);
         if (LOCALHOST_IPS.contains(hostAddress) && clusterIp == null) {
            log.error("This machine is configured to resolve its hostname to 127.0.0.1; this is " +
                  "an invalid configuration for clustering. Make sure `hostname -i` does not return 127.0.0.1 or ::1 " +
                  " or set -D{}=x.x.x.x to use different address. " +
                  "(if you set that to 127.0.0.1 you won't be able to connect from agents on other machines).",
                  Properties.CONTROLLER_CLUSTER_IP);
            return Future.failedFuture("Hostname resolves to 127.0.0.1");
         }
         // We are using numeric address because if this is running in a pod its hostname
         // wouldn't be resolvable even within the cluster/namespace.
         options.getEventBusOptions().setHost(hostAddress).setClusterPublicHost(hostAddress);

         // Do not override if it's manually set for some special reason
         if (System.getProperty("jgroups.tcp.address") == null) {
            System.setProperty("jgroups.tcp.address", hostAddress);
         }
         String clusterPort = Properties.get(Properties.CONTROLLER_CLUSTER_PORT, null);
         if (isController && clusterPort != null && System.getProperty("jgroups.tcp.port") == null) {
            System.setProperty("jgroups.tcp.port", clusterPort);
         }

         if (!isController) {
            String initialHosts = clusterIp;
            if (clusterPort != null)
               initialHosts = String.format("%s[%s]", initialHosts, clusterPort);

            log.info("Starting agent with controller: {}", initialHosts);
            System.setProperty("jgroups.tcpping.initial_hosts", initialHosts);
            System.setProperty(CLUSTER_JGROUPS_STACK, "jgroups-tcp-agent.xml");
         }
      } catch (UnknownHostException e) {
         log.error("Cannot lookup hostname", e);
         return Future.failedFuture("Cannot lookup hostname");
      }
      // JChannel substitutes ${...} in the stack files from System.getProperty at parse time, so the properties
      // set above take effect without being handed to the cluster manager explicitly.
      JGroupsClusterManager clusterManager = new JGroupsClusterManager(
            Properties.get(CLUSTER_JGROUPS_STACK, "jgroups-tcp.xml"),
            Properties.get(Properties.CLUSTER_NAME, "hyperfoil"));
      return Vertx.builder()
            .with(options)
            .withClusterManager(clusterManager)
            .buildClustered()
            .onSuccess(vertx -> {
               // Only now is the channel connected and its address known.
               populateProperties(clusterManager.channel());
               Codecs.register(vertx);
               ensureNettyResourceLeakDetection();
            })
            .onFailure(error -> log.error("Cannot start Vert.x", error));
   }

   /**
    * Publishes the address agents must dial back on. Agents read these as
    * {@code jgroups.tcpping.initial_hosts}, so they have to be what the transport actually bound to: if 7800 was
    * taken and TCP fell back to 7801, {@code getBindPort()} still reports the configured 7800.
    */
   private static void populateProperties(JChannel channel) {
      TP tp = channel.getProtocolStack().getTransport();
      InetAddress address;
      int port;
      PhysicalAddress bound = tp.getPhysicalAddressFromCache(channel.getAddress());
      if (bound instanceof IpAddress ip) {
         address = ip.getIpAddress();
         port = ip.getPort();
      } else {
         log.warn("Cannot determine the bound address of {}, falling back to the configured one", channel.getAddress());
         address = tp.getBindAddress();
         port = tp.getBindPort();
      }
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, address.getHostAddress());
      System.setProperty(Properties.CONTROLLER_CLUSTER_PORT, String.valueOf(port));
      log.info("Using {}:{} as clustering address", address.getHostAddress(), port);
   }

   private static InetAddress getAddressWithBestMatch(InetAddress controllerAddress) {
      InetAddress address = null;
      try {
         List<InetAddress> allAddresses = Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(nic -> {
            try {
               return !nic.isLoopback() && nic.isUp();
            } catch (SocketException e) {
               log.warn("Error enumerating NIC {}", nic, e);
               return false;
            }
         }).flatMap(nic -> Collections.list(nic.getInetAddresses()).stream()).collect(Collectors.toList());
         log.info("Agent must choose NIC with best subnet match to controller ({}/{}), available IPs: {} (loopback is ignored)",
               controllerAddress.getHostName(), controllerAddress.getHostAddress(), allAddresses);
         int longestMatch = -1;
         BitSet controllerBits = BitSet.valueOf(controllerAddress.getAddress());
         for (InetAddress a : allAddresses) {
            if (a.getAddress().length != controllerAddress.getAddress().length) {
               log.debug("Ignoring {} as this has different address length", a);
               continue;
            }
            BitSet aBits = BitSet.valueOf(a.getAddress());
            int i = 0;
            while (i < aBits.length() && aBits.get(i) == controllerBits.get(i)) {
               ++i;
            }
            log.debug("{} and {} have common prefix {} bits", controllerAddress, a, i);
            if (i > longestMatch) {
               longestMatch = i;
               address = a;
            }
         }
      } catch (SocketException e) {
         log.warn("Error enumerating NICs", e);
      }
      return address;
   }

   static void deploy(Vertx vertx, Class<? extends Verticle> verticleClass) {
      log.info("Deploying {}...", verticleClass.getSimpleName());
      vertx.deployVerticle(verticleClass, new DeploymentOptions()).onComplete(event -> {
         if (event.succeeded()) {
            log.info("{} deployed.", verticleClass.getSimpleName());
         } else {
            log.error("Failed to deploy " + verticleClass.getSimpleName(), event.cause());
            System.exit(1);
         }
      });
   }

   static void ensureNettyResourceLeakDetection() {
      // Vert.x disables Netty's memory leak detection in VertxImpl static ctor - we need to revert that
      String leakDetectionLevel = System.getProperty("io.netty.leakDetection.level",
            System.getProperty("io.netty.leakDetectionLevel"));
      if (leakDetectionLevel != null) {
         leakDetectionLevel = leakDetectionLevel.trim();
         for (ResourceLeakDetector.Level level : ResourceLeakDetector.Level.values()) {
            if (leakDetectionLevel.equalsIgnoreCase(level.name())
                  || leakDetectionLevel.equals(String.valueOf(level.ordinal()))) {
               ResourceLeakDetector.setLevel(level);
               return;
            }
         }
         log.warn("Cannot parse Netty leak detection level '{}', use one of: {}",
               leakDetectionLevel, ResourceLeakDetector.Level.values());
      }
      ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
   }

   public static Future<Void> shutdownVertx(Vertx vertx) {
      // Closing Vert.x calls ClusterManager.leave(), which disconnects and closes the JGroups channel.
      return vertx.close();
   }

   public static class Agent extends Hyperfoil {
      public static void main(String[] args) {
         logVersion();
         clusteredVertx(false)
               .onSuccess(vertx -> deploy(vertx, AgentVerticle.class))
               .onFailure(error -> System.exit(1));
      }
   }

   public static class Controller extends Hyperfoil {
      public static void main(String[] args) {
         logVersion();
         clusteredVertx(true)
               .onSuccess(vertx -> deploy(vertx, ControllerVerticle.class))
               .onFailure(error -> System.exit(1));
      }
   }

   public static class Standalone extends Hyperfoil {
      public static void main(String[] args) {
         logVersion();
         Thread.setDefaultUncaughtExceptionHandler(Hyperfoil::defaultUncaughtExceptionHandler);
         log.info("Starting non-clustered Vert.x...");
         Vertx vertx = Vertx.vertx();
         ensureNettyResourceLeakDetection();
         Codecs.register(vertx);
         deploy(vertx, ControllerVerticle.class);
      }
   }

   private static void defaultUncaughtExceptionHandler(Thread thread, Throwable throwable) {
      log.error(new FormattedMessage("Uncaught exception in thread {}({})", thread.getName(), thread.getId()), throwable);
   }

   private static void logVersion() {
      log.info("Java: {} {} {} {} ({}), CWD {}",
            System.getProperty("java.vm.vendor", "<unknown VM vendor>"),
            System.getProperty("java.vm.name", "<unknown VM name>"),
            System.getProperty("java.version", "<unknown version>"),
            System.getProperty("java.vm.version", "<unknown VM version>"),
            System.getProperty("java.home", "<unknown Java home>"),
            System.getProperty("user.dir", "<unknown current dir>"));
      String path = new File(Hyperfoil.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile()
            .getParent();
      log.info("Hyperfoil: {} ({})", Version.VERSION, Version.COMMIT_ID);
      log.info("           DISTRIBUTION:  {}", path);
      log.info("           ROOT_DIR:      {}", io.hyperfoil.internal.Controller.ROOT_DIR);
      log.info("           BENCHMARK_DIR: {}", io.hyperfoil.internal.Controller.BENCHMARK_DIR);
      log.info("           RUN_DIR:       {}", io.hyperfoil.internal.Controller.RUN_DIR);
      log.info("           HOOKS_DIR:     {}", io.hyperfoil.internal.Controller.HOOKS_DIR);
      System.getProperties().forEach((n, value) -> {
         String name = String.valueOf(n);
         if (name.startsWith("io.hyperfoil.") || name.startsWith("jgroups.")) {
            log.debug("System property {} = {}", name, value);
         }
      });
   }
}
