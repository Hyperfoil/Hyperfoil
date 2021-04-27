package io.hyperfoil.deploy.ssh;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClientCreator;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.io.NullOutputStream;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.DeploymentException;
import io.hyperfoil.internal.Properties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SshDeployedAgent implements DeployedAgent {
   private static final Logger log = LogManager.getLogger(SshDeployedAgent.class);
   private static final String PROMPT = "<_#%@_hyperfoil_@%#_>";
   private static final String DEBUG_ADDRESS = Properties.get(Properties.AGENT_DEBUG_PORT, null);
   private static final String DEBUG_SUSPEND = Properties.get(Properties.AGENT_DEBUG_SUSPEND, "n");
   private static final String AGENTLIB = "/agentlib";

   final String name;
   final String runId;
   final String username;
   final String hostname;
   final int port;
   final String dir;
   final String extras;
   final String cpu;

   private ClientSession session;
   private ChannelShell shellChannel;
   private Consumer<Throwable> exceptionHandler;
   private ScpClient scpClient;
   private PrintStream commandStream;

   public SshDeployedAgent(String name, String runId, String username, String hostname, int port, String dir, String extras, String cpu) {
      this.name = name;
      this.runId = runId;
      this.username = username;
      this.hostname = hostname;
      this.port = port;
      this.dir = dir;
      this.extras = extras;
      this.cpu = cpu;
   }

   @Override
   public void stop() {
      log.info("Stopping agent " + name);
      if (commandStream != null) {
         commandStream.close();
      }
      try {
         if (shellChannel != null) {
            shellChannel.close();
         }
      } catch (IOException e) {
         log.error("Failed closing shell", e);
      }
      try {
         session.close();
      } catch (IOException e) {
         log.error("Failed closing SSH session", e);
      }
   }

   public void deploy(ClientSession session, Consumer<Throwable> exceptionHandler) {
      this.session = session;
      this.exceptionHandler = exceptionHandler;

      this.scpClient = ScpClientCreator.instance().createScpClient(session);

      try {
         this.shellChannel = session.createShellChannel();
         shellChannel.setStreaming(ClientChannel.Streaming.Async);
         shellChannel.setErr(new NullOutputStream());
         OpenFuture open = shellChannel.open();
         if (!open.await(SshDeployer.TIMEOUT)) {
            exceptionHandler.accept(new DeploymentException("Shell not opened within timeout", null));
         }
         if (!open.isOpened()) {
            exceptionHandler.accept(new DeploymentException("Could not open shell", open.getException()));
         }
      } catch (IOException e) {
         exceptionHandler.accept(new DeploymentException("Failed to open shell", e));
      }

      IoOutputStream inStream = shellChannel.getAsyncIn();
      commandStream = new PrintStream(new OutputStream() {
         ByteArrayBuffer buffer = new ByteArrayBuffer();

         @Override
         public void write(byte[] b) throws IOException {
            buffer.clear(false);
            buffer.putRawBytes(b, 0, b.length);
            if (!inStream.writePacket(buffer).await()) {
               throw new IOException("Failed waiting for the write");
            }
         }

         @Override
         public void write(byte[] b, int off, int len) throws IOException {
            buffer.clear(false);
            buffer.putRawBytes(b, off, len);
            if (!inStream.writePacket(buffer).await()) {
               throw new IOException("Failed waiting for the write");
            }
         }

         @Override
         public void write(int b) throws IOException {
            buffer.clear(false);
            buffer.putByte((byte) b);
            if (!inStream.writePacket(buffer).await()) {
               throw new IOException("Failed waiting for the write");
            }
         }

         @Override
         public void close() throws IOException {
            inStream.close();
         }
      });
      runCommand("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'", true);

      runCommand("mkdir -p " + dir + AGENTLIB, true);

      Map<String, String> remoteMd5 = getRemoteMd5();
      Map<String, String> localMd5 = getLocalMd5();
      if (localMd5 == null) {
         return;
      }

      StringBuilder startAgentCommmand = new StringBuilder();
      if (cpu != null) {
         startAgentCommmand.append("taskset -c ").append(cpu).append(' ');
      }
      String java = Properties.get(Properties.AGENT_JAVA_EXECUTABLE, "java");
      startAgentCommmand.append(java).append(" -cp ");

      for (Map.Entry<String, String> entry : localMd5.entrySet()) {
         int lastSlash = entry.getKey().lastIndexOf("/");
         String filename = lastSlash < 0 ? entry.getKey() : entry.getKey().substring(lastSlash + 1);
         String remoteChecksum = remoteMd5.remove(filename);
         if (!entry.getValue().equals(remoteChecksum)) {
            log.debug("MD5 mismatch {}/{}, copying {}", entry.getValue(), remoteChecksum, entry.getKey());
            try {
               scpClient.upload(entry.getKey(), dir + AGENTLIB + "/" + filename, ScpClient.Option.PreserveAttributes);
            } catch (IOException e) {
               exceptionHandler.accept(e);
               return;
            }
         }
         startAgentCommmand.append(dir).append(AGENTLIB).append('/').append(filename).append(':');
      }
      if (!remoteMd5.isEmpty()) {
         StringBuilder rmCommand = new StringBuilder();
         // Drop those files that are not on classpath
         rmCommand.append("rm --interactive=never ");
         for (Map.Entry<String, String> entry : remoteMd5.entrySet()) {
            rmCommand.append(' ' + dir + AGENTLIB + '/' + entry.getKey());
         }
         runCommand(rmCommand.toString(), true);
      }
      String log4jConfigurationFile = Properties.get(Properties.LOG4J2_CONFIGURATION_FILE, null);
      if (log4jConfigurationFile != null) {
         if (log4jConfigurationFile.startsWith("file://")) {
            log4jConfigurationFile = log4jConfigurationFile.substring("file://".length());
         }
         String filename = log4jConfigurationFile.substring(log4jConfigurationFile.lastIndexOf(File.separatorChar) + 1);
         try {
            String targetFile = dir + AGENTLIB + "/" + filename;
            scpClient.upload(log4jConfigurationFile, targetFile, ScpClient.Option.PreserveAttributes);
            startAgentCommmand.append(" -D").append(Properties.LOG4J2_CONFIGURATION_FILE)
                  .append("=file://").append(targetFile);
         } catch (IOException e) {
            log.error("Cannot copy log4j2 configuration file.", e);
         }
      }

      startAgentCommmand.append(" -Djava.net.preferIPv4Stack=true");
      startAgentCommmand.append(" -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory");
      startAgentCommmand.append(" -D").append(Properties.AGENT_NAME).append('=').append(name);
      startAgentCommmand.append(" -D").append(Properties.RUN_ID).append('=').append(runId);
      startAgentCommmand.append(" -D").append(Properties.CONTROLLER_CLUSTER_IP).append('=').append(Properties.get(Properties.CONTROLLER_CLUSTER_IP, ""));
      startAgentCommmand.append(" -D").append(Properties.CONTROLLER_CLUSTER_PORT).append('=').append(Properties.get(Properties.CONTROLLER_CLUSTER_PORT, ""));
      if (DEBUG_ADDRESS != null) {
         startAgentCommmand.append(" -agentlib:jdwp=transport=dt_socket,server=y,suspend=").append(DEBUG_SUSPEND).append(",address=").append(DEBUG_ADDRESS);
      }
      if (extras != null) {
         startAgentCommmand.append(" ").append(extras);
      }
      startAgentCommmand.append(" io.hyperfoil.Hyperfoil\\$Agent &> ")
            .append(dir).append(File.separatorChar).append("agent.").append(name).append(".log");
      String startAgent = startAgentCommmand.toString();
      log.info("Starting agent {}", name);
      log.debug("Command: {}", startAgent);
      runCommand(startAgent, false);
      onPrompt(new StringBuilder(), new ByteArrayBuffer(),
            () -> exceptionHandler.accept(new BenchmarkExecutionException("Agent process terminated prematurely. Hint: type 'log " + name + "' to see agent output.")));
   }

   private void onPrompt(StringBuilder sb, ByteArrayBuffer buffer, Runnable completion) {
      buffer.clear(false);
      shellChannel.getAsyncOut().read(buffer).addListener(future -> {
         byte[] buf = new byte[future.getRead()];
         future.getBuffer().getRawBytes(buf);
         String str = new String(buf, StandardCharsets.UTF_8);
         log.info("Read: " + str);
         sb.append(str);
         if (sb.indexOf(PROMPT) >= 0) {
            completion.run();
         } else {
            if (sb.length() >= PROMPT.length()) {
               sb.delete(0, sb.length() - PROMPT.length());
            }
            onPrompt(sb, buffer, completion);
         }
      });
   }

   private String runCommand(String cmd, boolean wait) {
      log.trace("Running command {}", cmd);
      commandStream.println(cmd);
      // add one more empty command so that we get PROMPT on the line alone
      commandStream.println();
      commandStream.flush();
      StringBuilder lines = new StringBuilder();
      ByteArrayBuffer buffer = new ByteArrayBuffer();
      byte[] buf = new byte[buffer.capacity()];
      try {
         for (; ; ) {
            buffer.clear(false);
            IoReadFuture future = shellChannel.getAsyncOut().read(buffer);
            if (!future.await(10, TimeUnit.SECONDS)) {
               exceptionHandler.accept(new BenchmarkExecutionException("Timed out waiting for SSH output"));
               return null;
            }
            buffer.getRawBytes(buf, 0, future.getRead());
            String line = new String(buf, 0, future.getRead(), StandardCharsets.UTF_8);
            int newLine = line.indexOf('\n');
            if (newLine >= 0) {
               if (!wait) {
                  return null;
               }
               // first line should be echo of the command and we'll ignore that
               lines.append(line.substring(newLine + 1));
               break;
            }
         }
         for (; ; ) {
            int prompt = lines.lastIndexOf(PROMPT + "\r\n");
            if (prompt >= 0) {
               lines.delete(prompt, lines.length());
               return lines.toString();
            }
            buffer.clear(false);
            IoReadFuture future = shellChannel.getAsyncOut().read(buffer);
            if (!future.await(10, TimeUnit.SECONDS)) {
               exceptionHandler.accept(new BenchmarkExecutionException("Timed out waiting for SSH output"));
               return null;
            }
            buffer.getRawBytes(buf, 0, future.getRead());
            lines.append(new String(buf, 0, future.getRead(), StandardCharsets.UTF_8));
         }
      } catch (IOException e) {
         exceptionHandler.accept(new DeploymentException("Error reading from shell", e));
         return null;
      }
   }

   private Map<String, String> getLocalMd5() {
      String classpath = System.getProperty("java.class.path");
      Map<String, String> md5map = new HashMap<>();
      for (String file : classpath.split(":")) {
         if (!file.endsWith(".jar")) {
            // ignore folders etc...
            continue;
         }
         try {
            Process process = new ProcessBuilder("md5sum", file).start();
            process.waitFor();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
               String line = reader.readLine();
               if (line == null) {
                  log.warn("No output for md5sum " + file);
                  continue;
               }
               int space = line.indexOf(' ');
               if (space < 0) {
                  log.warn("Wrong output for md5sum " + file + ": " + line);
                  continue;
               }
               ;
               String checksum = line.substring(0, space);
               md5map.put(file, checksum);
            }
         } catch (IOException e) {
            log.info("Cannot get md5sum for " + file, e);
         } catch (InterruptedException e) {
            log.info("Interrupted waiting for md5sum" + file);
            Thread.currentThread().interrupt();
            return null;
         }
      }
      return md5map;
   }

   private Map<String, String> getRemoteMd5() {
      String[] lines = runCommand("md5sum " + dir + AGENTLIB + "/*", true).split("\r*\n");
      Map<String, String> md5map = new HashMap<>();
      for (String line : lines) {
         if (line.isEmpty()) {
            continue;
         } else if (line.endsWith("No such file or directory")) {
            break;
         }
         int space = line.indexOf(' ');
         if (space < 0) break;
         String checksum = line.substring(0, space);
         int fileIndex = line.lastIndexOf('/');
         if (fileIndex < 0) {
            fileIndex = space;
         }
         String file = line.substring(fileIndex + 1).trim();
         md5map.put(file, checksum);
      }
      return md5map;
   }

   public void downloadLog(ClientSession session, long offset, String destinationFile, Handler<AsyncResult<Void>> handler) {
      try (SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session)) {
         try (SftpClient.CloseableHandle handle = sftpClient.open(dir + File.separatorChar + "agent." + name + ".log")) {
            byte[] buffer = new byte[65536];
            try (FileOutputStream output = new FileOutputStream(destinationFile)) {
               long readOffset = offset;
               for (; ; ) {
                  int nread = sftpClient.read(handle, readOffset, buffer);
                  if (nread < 0) {
                     break;
                  }
                  output.write(buffer, 0, nread);
                  readOffset += nread;
               }
            }
         }
         handler.handle(Future.succeededFuture());
      } catch (IOException e) {
         handler.handle(Future.failedFuture(e));
      }
   }
}
