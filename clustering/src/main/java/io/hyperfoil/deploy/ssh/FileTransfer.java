package io.hyperfoil.deploy.ssh;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.scp.client.ScpClient;
import org.apache.sshd.scp.client.ScpClientCreator;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import io.hyperfoil.internal.Properties;

public class FileTransfer implements AutoCloseable {

   private static final Logger log = LogManager.getLogger(FileTransfer.class);

   private IFileTransfer client;

   FileTransfer(ClientSession session) throws Exception {
      String fileTransferClient = Properties.get(Properties.FILE_TRANSFER_CLIENT, "scp");
      log.info("Using {} protocol for file transfer", fileTransferClient);
      if ("scp".equals(fileTransferClient)) {
         this.client = new Scp(session);
      } else if ("sftp".equals(fileTransferClient)) {
         this.client = new Sftp(session);
      } else {
         throw new IllegalArgumentException("Invalid file transfer client. You provided: " + fileTransferClient);
      }
   }

   public void upload(String key, String remote) throws IOException {
      this.client.upload(key, remote);
   }

   @Override
   public void close() throws IOException {
      this.client.close();
   }

   private interface IFileTransfer {
      void upload(String key, String remote) throws IOException;

      void close() throws IOException;
   }

   private static class Scp implements IFileTransfer {

      private ScpClient scpClient;

      Scp(ClientSession session) {
         this.scpClient = ScpClientCreator.instance().createScpClient(session);
      }

      public void upload(String key, String remote) throws IOException {
         this.scpClient.upload(key, remote, ScpClient.Option.PreserveAttributes);
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
      public void upload(String key, String remote) throws IOException {
         try (InputStream in = new FileInputStream(key);
               OutputStream out = sftpClient.write(remote)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
               out.write(buffer, 0, len);
            }
         }
      }

      @Override
      public void close() throws IOException {
         this.sftpClient.close();
      }
   }
}
