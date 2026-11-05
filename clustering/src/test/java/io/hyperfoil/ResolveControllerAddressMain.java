package io.hyperfoil;

import java.net.InetAddress;

/**
 * Helper executed inside a container by {@link ControllerClusterIpTest}. Prints the hostname-derived address and the
 * address {@link ClusterAddressResolver#resolve(String)} would advertise to the agents.
 * <p>
 * Must stay dependency-free: inside the container only this class and {@link ClusterAddressResolver} are available.
 */
public class ResolveControllerAddressMain {
   public static final String LOCAL_HOST_PREFIX = "getLocalHost=";
   public static final String RESOLVED_PREFIX = "resolved=";

   public static void main(String[] args) throws Exception {
      System.out.println(LOCAL_HOST_PREFIX + InetAddress.getLocalHost().getHostAddress());
      System.out.println(RESOLVED_PREFIX + ClusterAddressResolver.resolve(null).getHostAddress());
   }
}
