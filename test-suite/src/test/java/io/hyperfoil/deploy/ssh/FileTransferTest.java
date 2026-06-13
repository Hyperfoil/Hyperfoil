package io.hyperfoil.deploy.ssh;

import static io.hyperfoil.deploy.ssh.FileTransfer.PROTOCOL_SCP;
import static io.hyperfoil.deploy.ssh.FileTransfer.PROTOCOL_SFTP;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.hyperfoil.internal.Properties;

public class FileTransferTest extends FileTransferBaseTest {

   @ParameterizedTest(name = "Testing upload targeting full path with protocol: {0}")
   @ValueSource(strings = { PROTOCOL_SCP, PROTOCOL_SFTP })
   public void testUploadTargetFullPath(String protocol) throws Exception {

      System.setProperty(Properties.FILE_TRANSFER_CLIENT, protocol);

      String fileName = UUID.randomUUID() + ".txt";
      Path localFile = Files.createFile(tempDir.resolve(fileName));

      // targetFile is a complete file path including the filename
      String targetFile = remoteDir.toAbsolutePath() + File.separator + fileName;

      try (FileTransfer fileTransfer = new FileTransfer(session)) {
         // no exception expected
         fileTransfer.upload(localFile.toAbsolutePath().toString(), targetFile);
      }

      // Assert the file exists exactly where we directed it
      assertTrue(Files.exists(remoteDir.resolve(fileName)), "File should be created at the exact explicit path.");
   }
}
