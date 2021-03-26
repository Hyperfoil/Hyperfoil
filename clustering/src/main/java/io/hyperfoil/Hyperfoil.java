package io.hyperfoil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;

import io.hyperfoil.api.Version;
import io.hyperfoil.clustering.ControllerVerticle;
import io.hyperfoil.clustering.AgentVerticle;
import io.hyperfoil.clustering.Codecs;
import io.hyperfoil.internal.Properties;
import io.netty.util.ResourceLeakDetector;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;

public class Hyperfoil {
   static final Logger log = LogManager.getLogger(Hyperfoil.class);
   private static final Set<String> LOCALHOST_IPS = new HashSet<>(Arrays.asList("127.0.0.1", "::1", "[::1]"));

   public static void clusteredVertx(boolean isController, Handler<Vertx> startedHandler, Runnable onError) {
      logVersion();
      Thread.setDefaultUncaughtExceptionHandler(Hyperfoil::defaultUncaughtExceptionHandler);
      log.info("Starting Vert.x...");
      VertxOptions options = new VertxOptions();
      try {
         String clusterIp = Properties.get(Properties.CONTROLLER_CLUSTER_IP, null);
         InetAddress address;
         if (isController && clusterIp != null) {
            address = InetAddress.getByName(clusterIp);
         } else {
            address = InetAddress.getLocalHost();
            if (!isController && LOCALHOST_IPS.contains(address.getHostAddress())) {
               InetAddress bestMatch = getAddressWithBestMatch(InetAddress.getByName(clusterIp));
               if (bestMatch != null) {
                  address = bestMatch;
               } else {
                  log.warn("No match found between controller IP and local addresses.");
               }
            }
         }
         String hostName = address.getHostName();
         String hostAddress = address.getHostAddress();
         log.info("Using host name {}/{}", hostName, hostAddress);
         if (LOCALHOST_IPS.contains(hostAddress) && clusterIp == null) {
            log.error("This machine is configured to resolve its hostname to 127.0.0.1; this is " +
                  "an invalid configuration for clustering. Make sure `hostname -i` does not return 127.0.0.1 or ::1 " +
                  " or set -D" + Properties.CONTROLLER_CLUSTER_IP + "=x.x.x.x to use different address. " +
                  "(if you set that to 127.0.0.1 you won't be able to connect from agents on other machines).");
            onError.run();
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
      } catch (UnknownHostException e) {
         log.error("Cannot lookup hostname", e);
         onError.run();
      }
      DefaultCacheManager cacheManager = createCacheManager();
      options.setClusterManager(new InfinispanClusterManager(cacheManager));
      Vertx.clusteredVertx(options, result -> {
         if (result.failed()) {
            log.error("Cannot start Vert.x", result.cause());
            onError.run();
         }
         Vertx vertx = result.result();
         Codecs.register(vertx);
         startedHandler.handle(vertx);
      });
      ensureNettyResourceLeakDetection();
   }

   private static InetAddress getAddressWithBestMatch(InetAddress controllerAddress) {
      InetAddress address = null;
      try {
         List<InetAddress> allAddresses = Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter(nic -> {
            try {
               return !nic.isLoopback() && nic.isUp();
            } catch (SocketException e) {
               log.warn("Error enumerating NIC " + nic, e);
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

   private static DefaultCacheManager createCacheManager() {
      try (InputStream stream = FileLookupFactory.newInstance().lookupFile("infinispan.xml", Thread.currentThread().getContextClassLoader())) {
         ConfigurationBuilderHolder holder = new ParserRegistry().parse(stream, null);
         holder.getGlobalConfigurationBuilder().transport().defaultTransport()
               .addProperty(JGroupsTransport.CHANNEL_LOOKUP, HyperfoilChannelLookup.class.getName())
               .initialClusterSize(1);
         return new DefaultCacheManager(holder, true);
      } catch (IOException e) {
         log.error("Cannot load Infinispan configuration");
         System.exit(1);
         return null;
      }
   }

   static void deploy(Vertx vertx, Class<? extends Verticle> verticleClass) {
      log.info("Deploying {}...", verticleClass.getSimpleName());
      vertx.deployVerticle(verticleClass, new DeploymentOptions(), event -> {
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
      String leakDetectionLevel = System.getProperty("io.netty.leakDetection.level");
      if (leakDetectionLevel != null) {
         leakDetectionLevel = leakDetectionLevel.trim();
         for (ResourceLeakDetector.Level level : ResourceLeakDetector.Level.values()) {
            if (leakDetectionLevel.equalsIgnoreCase(level.name()) || leakDetectionLevel.equals(String.valueOf(level.ordinal()))) {
               ResourceLeakDetector.setLevel(level);
               return;
            }
         }
         log.warn("Cannot parse Netty leak detection level '{}', use one of: {}",
               leakDetectionLevel, ResourceLeakDetector.Level.values());
      }
      ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.SIMPLE);
   }

   public static void shutdownVertx(Vertx vertx, Handler<AsyncResult<Void>> handler) {
      ClusterManager clusterManager = ((VertxInternal) vertx).getClusterManager();
      DefaultCacheManager cacheManager = (DefaultCacheManager) ((InfinispanClusterManager) clusterManager).getCacheContainer();
      vertx.close(result -> {
         try {
            cacheManager.close();
         } catch (IOException e) {
            log.error("Failed to close Infinispan cache manager", e);
         } finally {
            if (handler != null) {
               handler.handle(result);
            }
         }
      });
   }

   public static class Agent extends Hyperfoil {
      public static void main(String[] args) {
         clusteredVertx(false, vertx -> deploy(vertx, AgentVerticle.class), () -> System.exit(1));
      }
   }

   public static class Controller extends Hyperfoil {
      public static void main(String[] args) {
         clusteredVertx(true, vertx -> deploy(vertx, ControllerVerticle.class), () -> System.exit(1));
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
      log.info("Java: {} {} {} {} ({})",
            System.getProperty("java.vm.vendor", "<unknown VM vendor>"),
            System.getProperty("java.vm.name", "<unknown VM name>"),
            System.getProperty("java.version", "<unknown version>"),
            System.getProperty("java.vm.version", "<unknown VM version>"),
            System.getProperty("java.home", "<unknown Java home>"));
      String path = new File(Hyperfoil.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile().getParent();
      log.info("Hyperfoil: {} ({})", Version.VERSION, Version.COMMIT_ID);
      log.info("           DISTRIBUTION:  {}", path);
      log.info("           ROOT_DIR:      {}", io.hyperfoil.internal.Controller.ROOT_DIR);
      log.info("           BENCHMARK_DIR: {}", io.hyperfoil.internal.Controller.BENCHMARK_DIR);
      log.info("           RUN_DIR:       {}", io.hyperfoil.internal.Controller.RUN_DIR);
      log.info("           HOOKS_DIR:     {}", io.hyperfoil.internal.Controller.HOOKS_DIR);
   }
}
