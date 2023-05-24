package io.hyperfoil;

import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Collections;

import org.infinispan.commons.util.FileLookupFactory;
import org.infinispan.remoting.transport.jgroups.JGroupsChannelLookup;
import org.jgroups.JChannel;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCPPING;
import org.jgroups.protocols.pbcast.GMS;

import io.hyperfoil.internal.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jgroups.util.Util;

public class HyperfoilChannelLookup implements JGroupsChannelLookup {
   private static final Logger log = LogManager.getLogger(HyperfoilChannelLookup.class);

   @Override
   public JChannel getJGroupsChannel(java.util.Properties p) {
      try (InputStream stream = FileLookupFactory.newInstance().lookupFile("jgroups-tcp.xml", Thread.currentThread().getContextClassLoader())) {
         JChannel channel = new JChannel(stream);
         TCPPING ping = channel.getProtocolStack().findProtocol(TCPPING.class);
         String controllerIP = Properties.get(Properties.CONTROLLER_CLUSTER_IP, null);
         String controllerPort = Properties.get(Properties.CONTROLLER_CLUSTER_PORT, null);
         if (controllerIP != null && controllerPort != null) {
            InetSocketAddress address = new InetSocketAddress(controllerIP, Integer.parseInt(controllerPort));
            log.info("Connecting to controller {}:{} ({}:{})", controllerIP, controllerPort,
                  address.getAddress().getHostAddress(), address.getPort());
            ping.initialHosts(Collections.singletonList(address));
         } else {
            log.info("Reducing join timeout.");
            GMS gms = channel.getProtocolStack().findProtocol(GMS.class);
            gms.setJoinTimeout(0);
         }
         TCP tcp = channel.getProtocolStack().findProtocol(TCP.class);
         System.setProperty(Properties.CONTROLLER_CLUSTER_IP, tcp.getBindAddress().getHostAddress());
         System.setProperty(Properties.CONTROLLER_CLUSTER_PORT, String.valueOf(tcp.getBindPort()));
         log.info("Using {}:{} as clustering address", tcp.getBindAddress().getHostAddress(), tcp.getBindPort());
         return channel;
      } catch (BindException e) {
         log.error("Cannot start JChannel, available addresses are " + Util.getAllAvailableAddresses(null), e);
         throw new RuntimeException(e);
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
