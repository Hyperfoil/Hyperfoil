package io.hyperfoil.deploy.ssh;

import static io.hyperfoil.deploy.ssh.FileTransfer.PROTOCOL_SCP;
import static io.hyperfoil.deploy.ssh.FileTransfer.PROTOCOL_SFTP;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The controller can stop the same agent twice: once when the run ends while the agent is still deploying
 * and again when the deployer returns late.
 * <p>
 * The agent works on a real SSH session and really transfers a file before being stopped.
 */
public class SshDeployedAgentTest extends BaseSshTest {

   @ParameterizedTest(name = "Testing stop is idempotent after a real transfer with protocol: {0}")
   @ValueSource(strings = { PROTOCOL_SCP, PROTOCOL_SFTP })
   public void testStopIsIdempotent(String protocol) throws Exception {
      TestSshDeployedAgent agent = new TestSshDeployedAgent("test-agent", "run-123", TEST_USER, TEST_HOST, null, TEST_PORT,
            remoteDir.toString(), null, null);
      agent.deploy(session, protocol, t -> {
      });

      Path agentLibDir = remoteDir.resolve("agentlib");
      assertTrue(Files.exists(agentLibDir), "agentlib directory should have been created on remote host");
      try (Stream<Path> files = Files.list(agentLibDir)) {
         assertTrue(files.findAny().isPresent(), "agentlib directory should contain transferred files");
      }
      assertTrue(session.isOpen());

      assertDoesNotThrow(() -> {
         agent.stop();
         agent.stop();
      });

      assertFalse(session.isOpen(), "stop() should close the SSH session");
   }

   /**
    * deploy() returns early when the file transfer cannot be created, leaving fileTransfer unset.
    * The controller still calls stop() on such an agent, which must not fail and must still close the SSH session.
    */
   @Test
   public void testStopWithoutFileTransfer() {
      SshDeployedAgent agent = new SshDeployedAgent("test-agent", "run-123", TEST_USER, TEST_HOST, null, TEST_PORT,
            remoteDir.toString(), null, null);
      agent.deploy(session, "invalid", t -> {
      });
      assertTrue(session.isOpen());

      assertDoesNotThrow(agent::stop);
      assertFalse(session.isOpen(), "stop() should close the SSH session even if the file transfer was never created");
   }
}
