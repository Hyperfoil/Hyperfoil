package io.hyperfoil.benchmark.clustering;

import java.util.Map;
import java.util.UUID;

import io.hyperfoil.deploy.ssh.TestSshDeployer;
import io.hyperfoil.internal.Properties;

final class ClusterTestProperties {
   static final String CLUSTER_NAME = "hyperfoil-test-" + UUID.randomUUID();

   private ClusterTestProperties() {
   }

   /**
    * Options for an agent deployed by a clustered test, with any extra JVM arguments appended.
    */
   static Map<String, String> agentOptions(String... extraJvmArgs) {

      /*
       * The jitter watchdog reports any event loop stall over its threshold as a run error. That is a signal about
       * benchmark quality, not about correctness, but these functional tests assert that the run produced no errors.
       * GitHub Actions runners are shared and routinely stall a JVM for just over the 100 ms default, so raise the
       * threshold far enough that only a pathological stall trips it.
       */
      StringBuilder extras = new StringBuilder("-D" + Properties.JITTER_WATCHDOG_THRESHOLD + "=60000");
      for (String arg : extraJvmArgs) {
         extras.append(' ').append(arg);
      }
      return Map.of("extras", extras.toString());
   }

   static void configureClusterProperties() {
      // The production SSH deployer uploads only the jars on the classpath; when the tests run from an IDE the
      // classpath is made of module output directories instead, so use the deployer that packs those into jars.
      // Must be set before io.hyperfoil.internal.Controller is initialized.
      System.setProperty(Properties.DEPLOYER, TestSshDeployer.Factory.NAME);
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
