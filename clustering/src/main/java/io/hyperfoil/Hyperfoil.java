package io.hyperfoil;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;

import io.hyperfoil.api.deployment.AgentProperties;
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
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;

class Hyperfoil {
   static final Logger log = LoggerFactory.getLogger(Controller.class);
   private static final Set<String> LOCALHOST_IPS = new HashSet<>(Arrays.asList("127.0.0.1", "::1", "[::1]"));

   static void clusteredVertx(Handler<Vertx> startedHandler) {
      logJavaVersion();
      Thread.setDefaultUncaughtExceptionHandler(Hyperfoil::defaultUncaughtExceptionHandler);
      log.info("Starting Vert.x...");
      VertxOptions options = new VertxOptions();
      options.getEventBusOptions().setClustered(true);
      try {
         String clusterIp = System.getProperty(AgentProperties.CONTROLLER_CLUSTER_IP);
         InetAddress address;
         if (clusterIp != null) {
            address = InetAddress.getByName(clusterIp);
         } else {
            address = InetAddress.getLocalHost();
         }
         String hostName = address.getHostName();
         String hostAddress = address.getHostAddress();
         log.info("Using host name {}/{}", hostName, hostAddress);
         if (LOCALHOST_IPS.contains(hostAddress) && clusterIp == null) {
            log.error("This machine is configured to resolve its hostname to 127.0.0.1; this is " +
                  "an invalid configuration for clustering. Make sure `hostname -i` does not return 127.0.0.1 or ::1 " +
                  " or set -D" + AgentProperties.CONTROLLER_CLUSTER_IP + "=x.x.x.x to use different address. " +
                  "(if you set that to 127.0.0.1 you won't be able to connect from agents on other machines).");
            System.exit(1);
         }
         // We are using numeric address because if this is running in a pod its hostname
         // wouldn't be resolvable even within the cluster/namespace.
         options.getEventBusOptions().setHost(hostName).setClusterPublicHost(hostAddress);

         // Do not override if it's manually set for some special reason
         if (System.getProperty("jgroups.tcp.address") == null) {
            System.setProperty("jgroups.tcp.address", hostAddress);
         }
         String clusterPort = System.getProperty(AgentProperties.CONTROLLER_CLUSTER_PORT);
         if (clusterPort != null && System.getProperty("jgroups.tcp.port") == null) {
            System.setProperty("jgroups.tcp.port", clusterPort);
         }
      } catch (UnknownHostException e) {
         log.error("Cannot lookup hostname", e);
         System.exit(1);
      }
      DefaultCacheManager cacheManager = createCacheManager();
      options.setClusterManager(new InfinispanClusterManager(cacheManager));
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

   private static DefaultCacheManager createCacheManager() {
      try (InputStream stream = FileLookupFactory.newInstance().lookupFile("infinispan.xml", Thread.currentThread().getContextClassLoader())) {
         ConfigurationBuilderHolder holder = new ParserRegistry().parse(stream);
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
            log.error("Failed to deploy {}.", event.cause(), verticleClass.getSimpleName());
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
         Thread.setDefaultUncaughtExceptionHandler(Hyperfoil::defaultUncaughtExceptionHandler);
         log.info("Starting non-clustered Vert.x...");
         Vertx vertx = Vertx.vertx();
         Codecs.register(vertx);
         deploy(vertx, ControllerVerticle.class);
      }
   }

   private static void defaultUncaughtExceptionHandler(Thread thread, Throwable throwable) {
      log.error("Uncaught exception in thread {}({})", throwable, thread.getName(), thread.getId());
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
