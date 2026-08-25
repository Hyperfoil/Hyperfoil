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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
 * mvn test -Dtest=FileTransferPerformanceTest -Dio.hyperfoil.file.transfer.client=scp
 * mvn test -Dtest=FileTransferPerformanceTest -Dio.hyperfoil.file.transfer.client=sftp
 */
@Tag("io.hyperfoil.test.Benchmark")
public class FileTransferPerformanceTest extends FileTransferBaseTest {

   private static final Logger log = LogManager.getLogger(FileTransferPerformanceTest.class);

   // Use fixed seed for reproducible results
   static final long SEED = 42L;
   static final int MIN_FILE_SIZE = 1024; // 1KB
   static final int MAX_FILE_SIZE = 1024 * 1024; // 1MB

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
