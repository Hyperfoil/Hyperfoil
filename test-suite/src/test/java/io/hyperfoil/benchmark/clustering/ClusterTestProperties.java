package io.hyperfoil.benchmark.clustering;

import java.util.UUID;

import io.hyperfoil.internal.Properties;

final class ClusterTestProperties {
   static final String CLUSTER_NAME = "hyperfoil-test-" + UUID.randomUUID();

   private ClusterTestProperties() {
   }

   static void configureClusterProperties() {
      // Some clustered tests time out in GitHub Actions because the agents don't cluster soon enough.
      System.setProperty("jgroups.join_timeout", "15000");
      // Controller properties
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
      // Keep this JVM's cluster distinct from any other Hyperfoil run on the same host.
      System.setProperty(Properties.CLUSTER_NAME, CLUSTER_NAME);
   }
}
