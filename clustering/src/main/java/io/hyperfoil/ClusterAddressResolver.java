package io.hyperfoil;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves the address the controller advertises to the agents.
 */
public final class ClusterAddressResolver {
   private ClusterAddressResolver() {
   }

   /**
    * When the user did not set {@code io.hyperfoil.controller.cluster.ip} we normally use the address the hostname
    * resolves to. On many machines {@code /etc/hosts} maps the
    * hostname to a loopback address such as {@code 127.0.0.1} or {@code 127.0.1.1}; agents on other machines cannot
    * connect to that. In that case we fall back to the address of a real network interface.
    *
    * @param clusterIp explicitly configured address, or null
    * @return the address to advertise; may still be loopback if the machine has no other interface
    */
   public static InetAddress resolve(String clusterIp) throws UnknownHostException {
      if (clusterIp != null) {
         return InetAddress.getByName(clusterIp);
      }
      InetAddress address = InetAddress.getLocalHost();
      if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
         InetAddress routable = firstNonLoopbackAddress();
         if (routable != null) {
            return routable;
         }
      }
      return address;
   }

   /**
    * @return address of an up, non-loopback interface, preferring site-local IPv4, or null if there is none.
    */
   public static InetAddress firstNonLoopbackAddress() {
      List<InetAddress> candidates = new ArrayList<>();
      try {
         for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (nic.isLoopback() || !nic.isUp()) {
               continue;
            }
            for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
               if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && !addr.isAnyLocalAddress()) {
                  candidates.add(addr);
               }
            }
         }
      } catch (SocketException e) {
         return null;
      }
      InetAddress ipv4 = null;
      for (InetAddress addr : candidates) {
         if (addr instanceof Inet4Address) {
            if (addr.isSiteLocalAddress()) {
               return addr;
            } else if (ipv4 == null) {
               ipv4 = addr;
            }
         }
      }
      if (ipv4 != null) {
         return ipv4;
      }
      return candidates.isEmpty() ? null : candidates.getFirst();
   }
}
