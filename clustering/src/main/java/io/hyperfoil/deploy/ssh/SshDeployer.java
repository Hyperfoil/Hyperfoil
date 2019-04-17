package io.hyperfoil.deploy.ssh;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.ConnectFuture;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.hyperfoil.api.deployment.DeploymentException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SshDeployer implements Deployer {
   private static final Logger log = LoggerFactory.getLogger(SshDeployer.class);

   private final SshClient client;

   private SshDeployer() {
      client = SshClient.setUpDefaultClient();
      client.start();
      client.setServerKeyVerifier((clientSession1, remoteAddress, serverKey) -> true);
   }

   @Override
   public DeployedAgent start(Agent agent, String runId, Consumer<Throwable> exceptionHandler) {
      String hostname = null, username = null;
      int port = -1;
      if (agent.inlineConfig != null) {
         int atIndex = agent.inlineConfig.indexOf('@');
         int colonIndex = agent.inlineConfig.lastIndexOf(':');
         hostname = agent.inlineConfig.substring(atIndex + 1, colonIndex >= 0 ? colonIndex : agent.inlineConfig.length());
         username = atIndex >= 0 ? agent.inlineConfig.substring(0, atIndex) : null;
         port = colonIndex >= 0 ? Integer.parseInt(agent.inlineConfig.substring(colonIndex + 1)) : -1;
      }
      if (agent.properties != null) {
         hostname = agent.properties.getOrDefault("host", hostname);
         username = agent.properties.getOrDefault("user", username);
         String portString = agent.properties.get("port");
         if (portString != null) {
            try {
               port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
               log.error("Failed to parse port number for " + agent.name + ": " + portString);
            }
         }
      }
      if (hostname == null) {
         hostname = agent.name;
      }
      if (port < 0) {
         port = 22;
      }
      if (username == null) {
         username = System.getProperty("user.name");
      }
      try {
         SshDeployedAgent deployedAgent = new SshDeployedAgent(agent.name, runId);
         ConnectFuture connect = client.connect(username, hostname, port).verify(15000);
         deployedAgent.deploy(connect.getSession(), exceptionHandler);
         return deployedAgent;
      } catch (IOException e) {
         exceptionHandler.accept(new DeploymentException("Cannot connect to agent " + agent.name + " at " + username + "@" + hostname + ":" + port, e));
         return null;
      }
   }

   @Override
   public void close() {
      client.stop();
   }

   @MetaInfServices(Deployer.Factory.class)
   public static class Factory implements Deployer.Factory {
      @Override
      public String name() {
         return "ssh";
      }

      @Override
      public SshDeployer create() {
         return new SshDeployer();
      }
   }
}
