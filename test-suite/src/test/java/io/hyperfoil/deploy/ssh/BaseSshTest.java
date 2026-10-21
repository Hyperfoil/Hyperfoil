package io.hyperfoil.deploy.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Base class for tests that need a real SSH session.
 * <p>
 * This test requires:
 * 1. SSH server running on localhost:22
 * 2. SSH key authentication configured (~/.ssh/id_rsa)
 * <p>
 * Set these system properties to configure the test:
 * - ssh.test.host (default: localhost)
 * - ssh.test.port (default: 22)
 * - ssh.test.user (default: current user)
 */
public abstract class BaseSshTest {

   private static final Logger log = LogManager.getLogger(BaseSshTest.class);

   private static final long LONG_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1);

   static final String TEST_HOST = System.getProperty("ssh.test.host", "localhost");
   static final int TEST_PORT = Integer.parseInt(System.getProperty("ssh.test.port", "22"));
   static final String TEST_USER = System.getProperty("ssh.test.user", System.getProperty("user.name"));

   @TempDir
   Path tempDir;

   @TempDir
   Path remoteDir;

   SshClient client;
   ClientSession session;

   @BeforeEach
   public void setup() throws Exception {
      client = SshClient.setUpDefaultClient();
      // Not Long.MAX_VALUE: SSHD converts these to nanoseconds (Duration.toNanos), which overflows
      PropertyResolverUtils.updateProperty(client, CoreModuleProperties.IDLE_TIMEOUT.getName(), LONG_TIMEOUT_MS);
      PropertyResolverUtils.updateProperty(client, CoreModuleProperties.NIO2_READ_TIMEOUT.getName(), LONG_TIMEOUT_MS);
      PropertyResolverUtils.updateProperty(client, CoreModuleProperties.NIO_WORKERS.getName(), 1);

      client.start();
      client.setServerKeyVerifier((clientSession, remoteAddress, serverKey) -> true);

      // Connect and authenticate
      session = client.connect(TEST_USER, TEST_HOST, TEST_PORT).verify(15000).getSession();
      session.auth().verify(10000);
   }

   @AfterEach
   public void teardown() throws Exception {
      if (session != null) {
         session.close();
      }

      if (client != null) {
         client.stop();
      }

      cleanupFolder(tempDir);
      cleanupFolder(remoteDir);
   }

   private void cleanupFolder(Path folder) throws IOException {
      try (var files = Files.list(folder)) {
         files.forEach(p -> {
            try {
               Files.deleteIfExists(p);
            } catch (IOException e) {
               log.warn("Failed to clean up {}", p, e);
            }
         });
      }
   }
}
