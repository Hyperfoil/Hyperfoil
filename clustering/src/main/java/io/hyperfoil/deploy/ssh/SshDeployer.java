package io.hyperfoil.deploy.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.function.Consumer;

import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.io.resource.URLResource;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Agent;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.Deployer;
import io.hyperfoil.api.deployment.DeploymentException;
import io.hyperfoil.clustering.Properties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SshDeployer implements Deployer {
   private static final Logger log = LoggerFactory.getLogger(SshDeployer.class);
   static final long TIMEOUT = 10000;

   private final SshClient client;

   private SshDeployer() {
      client = SshClient.setUpDefaultClient();

      PropertyResolverUtils.updateProperty(client, ClientFactoryManager.IDLE_TIMEOUT, Long.MAX_VALUE);
      PropertyResolverUtils.updateProperty(client, ClientFactoryManager.NIO2_READ_TIMEOUT, Long.MAX_VALUE);
      PropertyResolverUtils.updateProperty(client, ClientFactoryManager.NIO_WORKERS, 1);

      client.start();
      client.setServerKeyVerifier((clientSession1, remoteAddress, serverKey) -> true);
   }

   @Override
   public DeployedAgent start(Agent agent, String runId, Consumer<Throwable> exceptionHandler) {
      String hostname = null, username = null;
      int port = -1;
      String dir = null, extras = null;
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
         dir = agent.properties.get("dir");
         extras = agent.properties.get("extras");
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
      if (dir == null) {
         Path defaultRoot = Paths.get(System.getProperty("java.io.tmpdir"), "hyperfoil");
         dir = Properties.get(Properties.ROOT_DIR, Paths::get, defaultRoot).toString();
      }
      try {
         SshDeployedAgent deployedAgent = new SshDeployedAgent(agent.name, runId, username, hostname, port, dir, extras);
         ClientSession session = connectAndLogin(username, hostname, port);
         deployedAgent.deploy(session, exceptionHandler);
         return deployedAgent;
      } catch (IOException | GeneralSecurityException e) {
         exceptionHandler.accept(new DeploymentException("Cannot connect to agent " + agent.name + " at " + username + "@" + hostname + ":" + port, e));
         return null;
      } catch (DeploymentException e) {
         exceptionHandler.accept(e);
         return null;
      }
   }

   @Override
   public void downloadAgentLog(DeployedAgent deployedAgent, long offset, String destinationFile, Handler<AsyncResult<Void>> handler) {
      SshDeployedAgent sshAgent = (SshDeployedAgent) deployedAgent;
      try {
         ClientSession session = connectAndLogin(sshAgent.username, sshAgent.hostname, sshAgent.port);
         sshAgent.downloadLog(session, offset, destinationFile, handler);
      } catch (IOException | DeploymentException | GeneralSecurityException e) {
         handler.handle(Future.failedFuture(e));
      }
   }

   private ClientSession connectAndLogin(String username, String hostname, int port) throws IOException, GeneralSecurityException, DeploymentException {
      ConnectFuture connect = client.connect(username, hostname, port).verify(15000);
      ClientSession session = connect.getSession();

      String userHome = System.getProperty("user.home");
      URLResource identity;
      identity = new URLResource(Paths.get(userHome, ".ssh", "id_rsa").toUri().toURL());

      try (InputStream inputStream = identity.openInputStream()) {
         session.addPublicKeyIdentity(GenericUtils.head(SecurityUtils.loadKeyPairIdentities(
               session,
               identity,
               inputStream,
               (s, resourceKey, retryIndex) -> null
         )));
      }

      AuthFuture auth = session.auth();
      if (!auth.await(TIMEOUT)) {
         throw new DeploymentException("Not authenticated within timeout", null);
      }
      if (!auth.isSuccess()) {
         throw new DeploymentException("Failed to authenticate", auth.getException());
      }
      return session;
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
