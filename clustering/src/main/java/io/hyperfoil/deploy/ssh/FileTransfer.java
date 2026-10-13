package io.hyperfoil.deploy.ssh;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.client.ScpClientCreator;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import io.hyperfoil.internal.Properties;

public class FileTransfer implements AutoCloseable {

   public static final String PROTOCOL_SCP = "scp";
   public static final String PROTOCOL_SFTP = "sftp";

   private static final Logger log = LogManager.getLogger(FileTransfer.class);

   private IFileTransfer client;

   FileTransfer(ClientSession session) throws Exception {
      String fileTransferClient = Properties.get(Properties.FILE_TRANSFER_CLIENT, PROTOCOL_SCP);
      log.info("Using {} protocol for file transfer", fileTransferClient);
      if (PROTOCOL_SCP.equals(fileTransferClient)) {
         this.client = new Scp(session);
      } else if (PROTOCOL_SFTP.equals(fileTransferClient)) {
         this.client = new Sftp(session);
      } else {
         throw new IllegalArgumentException("Invalid file transfer client. You provided: " + fileTransferClient);
      }
   }

   public void upload(String local, String remote) throws IOException {
      this.upload(List.of(local), remote);
   }

   public void upload(List<String> local, String remote) throws IOException {
      this.client.upload(local, remote);
   }

   @Override
   public void close() throws IOException {
      this.client.close();
   }

   private interface IFileTransfer {

      void upload(List<String> local, String remote) throws IOException;

      void close() throws IOException;
   }

   private static class Scp implements IFileTransfer {

      private ScpClient scpClient;

      Scp(ClientSession session) {
         this.scpClient = ScpClientCreator.instance().createScpClient(session);
      }

      @Override
      public void upload(List<String> local, String remote) throws IOException {
         if (!local.isEmpty()) {
            this.scpClient.upload(local.toArray(String[]::new), remote, ScpClient.Option.PreserveAttributes);
         }
      }

      @Override
      public void close() {

      }
   }

   private static class Sftp implements IFileTransfer {

      private SftpClient sftpClient;

      Sftp(ClientSession session) throws Exception {
         try {
            this.sftpClient = SftpClientFactory.instance().createSftpClient(session);
         } catch (IOException e) {
            throw new Exception("Problem while creating the Sftp instance", e);
         }
      }

      @Override
      public void upload(List<String> localFiles, String remote) throws IOException {
         for (String localFile : localFiles) {

            String localFileName = new File(localFile).getName();

            String targetPath;
            if (remote.endsWith(localFileName)) {
               // Scenario 1: remote is already an exact file path (e.g., /dir/agentlib/log4j2.xml)
               targetPath = remote;
            } else if (remote.endsWith(File.separator)) {
               // Scenario 2: remote is a directory path ending with a slash (e.g., /dir/agentlib/)
               targetPath = remote + localFileName;
            } else {
               // Scenario 3: remote is a directory path without a trailing slash (e.g., /dir/agentlib)
               targetPath = remote + File.separator + localFileName;
            }

            try (InputStream in = new FileInputStream(localFile);
                  OutputStream out = sftpClient.write(targetPath)) {
               byte[] buffer = new byte[32768];
               int len;
               while ((len = in.read(buffer)) != -1) {
                  out.write(buffer, 0, len);
               }
            }
         }
      }

      @Override
      public void close() throws IOException {
         this.sftpClient.close();
      }
   }
}
