package io.hyperfoil.benchmark.clustering;

import io.hyperfoil.internal.Properties;

final class ClusterTestProperties {

   static void configureClusterProperties() {
      // Some clustered tests time out in GitHub Actions because the agents don't cluster soon enough.
      System.setProperty("jgroups.join_timeout", "15000");
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
   }
}
