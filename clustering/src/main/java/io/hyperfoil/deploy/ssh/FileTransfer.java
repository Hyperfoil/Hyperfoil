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

public class FileTransfer {

   private static final Logger log = LogManager.getLogger(FileTransfer.class);

   private IFileTransfer client;

   FileTransfer(ClientSession session) {
      String fileTransferClient = System.getProperty(Properties.FILE_TRANSFER_CLIENT, "scp");
      log.info("Using {} protocol for file transfer", fileTransferClient);
      if ("scp".equals(fileTransferClient)) {
         this.client = new Scp(session);
      } else if ("sftp".equals(fileTransferClient)) {
         this.client = new Sftp(session);
      } else {
         throw new RuntimeException("Invalid file transfer client. You provided: " + fileTransferClient);
      }
   }

   public void upload(String key, String remote) throws IOException {
      this.client.upload(key, remote);
   }

   private interface IFileTransfer {
      void upload(String key, String remote) throws IOException;
   }

   private static class Scp implements IFileTransfer {

      private ScpClient scpClient;

      Scp(ClientSession session) {
         this.scpClient = ScpClientCreator.instance().createScpClient(session);
      }

      public void upload(String key, String remote) throws IOException {
         this.scpClient.upload(key, remote, ScpClient.Option.PreserveAttributes);
      }
   }

   private static class Sftp implements IFileTransfer {

      private SftpClient sftpClient;

      Sftp(ClientSession session) {
         try {
            this.sftpClient = SftpClientFactory.instance().createSftpClient(session);
         } catch (IOException e) {
            try {
               session.close();
            } catch (IOException ex) {
               throw new RuntimeException("Problem while closing the session", ex);
            }
            throw new RuntimeException("Problem while creating the Sftp instance", e);
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
   }
}
