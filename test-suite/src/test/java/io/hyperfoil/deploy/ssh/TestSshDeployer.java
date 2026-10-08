package io.hyperfoil.deploy.ssh;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.deployment.Deployer;

/**
 * SSH deployer used only by the test suite
 */
public class TestSshDeployer extends SshDeployer {

   @Override
   protected SshDeployedAgent createAgent(String name, String runId, String username, String hostname, String sshKey,
         int port, String dir, String extras, String cpu) {
      return new TestSshDeployedAgent(name, runId, username, hostname, sshKey, port, dir, extras, cpu);
   }

   @MetaInfServices(Deployer.Factory.class)
   public static class Factory implements Deployer.Factory {
      public static final String NAME = "test-ssh";

      @Override
      public String name() {
         return NAME;
      }

      @Override
      public TestSshDeployer create() {
         return new TestSshDeployer();
      }
   }
}
