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

public class HyperfoilChannelLookup implements JGroupsChannelLookup {

   @Override
   public JChannel getJGroupsChannel(Properties p) {
      try (InputStream stream = FileLookupFactory.newInstance().lookupFile("jgroups-tcp.xml", Thread.currentThread().getContextClassLoader())) {
         JChannel channel = new JChannel(stream);
         TCPPING ping = channel.getProtocolStack().findProtocol(TCPPING.class);
         String controllerIP = System.getProperty(io.hyperfoil.clustering.Properties.CONTROLLER_CLUSTER_IP);
         String controllerPort = System.getProperty(io.hyperfoil.clustering.Properties.CONTROLLER_CLUSTER_PORT);
         if (controllerIP != null && controllerPort != null) {
            ping.initialHosts(Collections.singletonList(InetSocketAddress.createUnresolved(controllerIP, Integer.parseInt(controllerPort))));
         }
         TCP tcp = channel.getProtocolStack().findProtocol(TCP.class);
         System.setProperty(io.hyperfoil.clustering.Properties.CONTROLLER_CLUSTER_IP, tcp.getBindAddress().getHostAddress());
         System.setProperty(io.hyperfoil.clustering.Properties.CONTROLLER_CLUSTER_PORT, String.valueOf(tcp.getBindPort()));
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
