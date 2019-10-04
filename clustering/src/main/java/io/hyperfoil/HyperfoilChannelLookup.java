package io.hyperfoil;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Properties;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.remoting.transport.jgroups.JGroupsChannelLookup;
import org.jgroups.JChannel;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCPPING;
import org.jgroups.protocols.pbcast.GMS;

import io.hyperfoil.api.deployment.AgentProperties;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class HyperfoilChannelLookup implements JGroupsChannelLookup {
   private static final Logger log = LoggerFactory.getLogger(HyperfoilChannelLookup.class);

   @Override
   public JChannel getJGroupsChannel(Properties p) {
      try (InputStream stream = FileLookupFactory.newInstance().lookupFile("jgroups-tcp.xml", Thread.currentThread().getContextClassLoader())) {
         JChannel channel = new JChannel(stream);
         TCPPING ping = channel.getProtocolStack().findProtocol(TCPPING.class);
         String controllerIP = System.getProperty(AgentProperties.CONTROLLER_CLUSTER_IP);
         String controllerPort = System.getProperty(AgentProperties.CONTROLLER_CLUSTER_PORT);
         if (controllerIP != null && controllerPort != null) {
            log.info("Connecting to controller {}:{}", controllerIP, controllerPort);
            ping.initialHosts(Collections.singletonList(new InetSocketAddress(controllerIP, Integer.parseInt(controllerPort))));
         } else {
            log.info("Reducing join timeout.");
            GMS gms = channel.getProtocolStack().findProtocol(GMS.class);
            gms.joinTimeout(0);
         }
         TCP tcp = channel.getProtocolStack().findProtocol(TCP.class);
         System.setProperty(AgentProperties.CONTROLLER_CLUSTER_IP, tcp.getBindAddress().getHostAddress());
         System.setProperty(AgentProperties.CONTROLLER_CLUSTER_PORT, String.valueOf(tcp.getBindPort()));
         log.info("Using {}:{} as clustering address", tcp.getBindAddress().getHostAddress(), tcp.getBindPort());
         return channel;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public boolean shouldConnect() {
      return true;
   }

   @Override
   public boolean shouldDisconnect() {
      return true;
   }

   @Override
   public boolean shouldClose() {
      return true;
   }
}
