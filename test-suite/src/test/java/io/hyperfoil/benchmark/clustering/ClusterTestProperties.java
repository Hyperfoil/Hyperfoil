package io.hyperfoil.benchmark.clustering;

import io.hyperfoil.deploy.ssh.TestSshDeployer;
import io.hyperfoil.internal.Properties;

final class ClusterTestProperties {

   static void configureClusterProperties() {
      // The production SSH deployer uploads only the jars on the classpath; when the tests run from an IDE the
      // classpath is made of module output directories instead, so use the deployer that packs those into jars.
      // Must be set before io.hyperfoil.internal.Controller is initialized.
      System.setProperty(Properties.DEPLOYER, TestSshDeployer.Factory.NAME);
      // Some clustered tests time out in GitHub Actions because the agents don't cluster soon enough.
      System.setProperty("jgroups.join_timeout", "15000");
      System.setProperty(Properties.CONTROLLER_HOST, "localhost");
      System.setProperty(Properties.CONTROLLER_PORT, "0");
      System.setProperty(Properties.CONTROLLER_CLUSTER_IP, "localhost");
   }
}
