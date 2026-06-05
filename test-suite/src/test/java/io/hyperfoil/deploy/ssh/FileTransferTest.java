package io.hyperfoil.deploy.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.internal.Properties;

/**
 * Performance test for FileTransfer class from SshDeployedAgent.
 * Tests the configured file transfer protocol (SCP or SFTP).
 * <p>
 * This test requires:
 * 1. SSH server running on localhost:22
 * 2. SSH key authentication configured (~/.ssh/id_rsa)
 * <p>
 * Set these system properties to configure the test:
 * - ssh.test.host (default: localhost)
 * - ssh.test.port (default: 22)
 * - ssh.test.user (default: current user)
 * - io.hyperfoil.file.transfer.client (default: scp) - set to "scp" or "sftp"
 * <p>
 * Example usage:
 * mvn test -Dtest=FileTransferTest -Dio.hyperfoil.file.transfer.client=scp
 * mvn test -Dtest=FileTransferTest -Dio.hyperfoil.file.transfer.client=sftp
 */
@Tag("io.hyperfoil.test.Benchmark")
public class FileTransferTest {

   private static final Logger log = LogManager.getLogger(FileTransferTest.class);

   private static final String TEST_HOST = System.getProperty("ssh.test.host", "localhost");
   private static final int TEST_PORT = Integer.parseInt(System.getProperty("ssh.test.port", "22"));
   private static final String TEST_USER = System.getProperty("ssh.test.user", System.getProperty("user.name"));

   // Use fixed seed for reproducible results
   private static final long SEED = 42L;
   private static final int MIN_FILE_SIZE = 1024; // 1KB
   private static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB

   @TempDir
   Path tempDir;

   @TempDir
   Path remoteDir;

   private SshClient client;
   private ClientSession session;

   @BeforeEach
   public void setup() throws Exception {
      client = SshClient.setUpDefaultClient();
      PropertyResolverUtils.updateProperty(client, CoreModuleProperties.IDLE_TIMEOUT.getName(), Long.MAX_VALUE);
      PropertyResolverUtils.updateProperty(client, CoreModuleProperties.NIO2_READ_TIMEOUT.getName(), Long.MAX_VALUE);
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
   }

   @Test
   public void testFileTransferPerformance() throws Exception {
      String protocol = System.getProperty(Properties.FILE_TRANSFER_CLIENT, "scp");

      log.info("=== FileTransfer Performance Test ===");
      log.info("Protocol: {}", protocol);
      log.info("Host: {}@{}:{}", TEST_USER, TEST_HOST, TEST_PORT);
      log.info("Remote directory: {}", remoteDir);
      log.info("Seed: " + SEED);

      int[] fileCounts = { 1, 10, 100, 1000 };

      for (int fileCount : fileCounts) {
         log.info("--- Testing with {} file(s) ---", fileCount);

         // Generate test files
         List<String> testFiles = generateTestFiles(fileCount);
         long totalSize = 0;
         for (String f : testFiles) {
            totalSize += new File(f).length();
         }
         log.info(String.format("Total size: %.2f MB", totalSize / (1024.0 * 1024.0)));

         // Test using configured protocol
         long transferTime = testFileTransfer(testFiles);
         log.info(String.format("%s: %6d ms (%.2f MB/s)",
               protocol.toUpperCase(),
               transferTime,
               (totalSize / (1024.0 * 1024.0)) / (transferTime / 1000.0)));

         // Verify files were uploaded
         verifyFilesUploaded(testFiles);
      }
   }

   private List<String> generateTestFiles(int count) throws IOException {
      List<String> files = new ArrayList<>();
      Random random = new Random(SEED);

      for (int i = 0; i < count; i++) {
         // Generate random file size between MIN and MAX
         int fileSize = MIN_FILE_SIZE + random.nextInt(MAX_FILE_SIZE - MIN_FILE_SIZE);

         File file = tempDir.resolve("test-file-" + i + ".dat").toFile();

         // Generate random content
         try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int remaining = fileSize;

            while (remaining > 0) {
               int toWrite = Math.min(buffer.length, remaining);
               random.nextBytes(buffer);
               fos.write(buffer, 0, toWrite);
               remaining -= toWrite;
            }
         }

         files.add(file.getAbsolutePath());
      }

      return files;
   }

   private long testFileTransfer(List<String> files) throws Exception {
      try (FileTransfer fileTransfer = new FileTransfer(session)) {
         long startTime = System.currentTimeMillis();

         fileTransfer.upload(files, remoteDir + "/");

         long endTime = System.currentTimeMillis();
         return endTime - startTime;
      }
   }

   private void verifyFilesUploaded(List<String> files) throws IOException {
      // Count files in remote directory
      long uploadedFileCount = Files.list(remoteDir).count();
      assertEquals(files.size(), uploadedFileCount,
            "Number of uploaded files should match number of source files");

      // Verify each file exists and has correct size
      for (String sourceFilePath : files) {
         File sourceFile = new File(sourceFilePath);
         Path remotePath = remoteDir.resolve(sourceFile.getName());
         assertTrue(Files.exists(remotePath),
               "File should exist in remote directory: " + sourceFile.getName());
         assertEquals(sourceFile.length(), Files.size(remotePath),
               "File size should match: " + sourceFile);
      }
   }
}
