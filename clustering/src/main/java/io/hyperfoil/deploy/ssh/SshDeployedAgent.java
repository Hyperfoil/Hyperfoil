package io.hyperfoil.deploy.ssh;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.scp.ScpClient;
import org.apache.sshd.client.scp.ScpClientCreator;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.client.subsystem.sftp.SftpClientFactory;
import org.apache.sshd.common.util.io.NullOutputStream;

import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.deployment.DeploymentException;
import io.hyperfoil.internal.Properties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SshDeployedAgent implements DeployedAgent {
   private static final Logger log = LoggerFactory.getLogger(SshDeployedAgent.class);
   private static final String PROMPT = "<_#%@_hyperfoil_@%#_>";
   private static final String DEBUG_ADDRESS = System.getProperty(Properties.AGENT_DEBUG_PORT);
   private static final String DEBUG_SUSPEND = Properties.get(Properties.AGENT_DEBUG_SUSPEND, "n");
   private static final String AGENTLIB = "/agentlib";

   final String name;
   final String runId;
   final String username;
   final String hostname;
   final int port;
   final String dir;
   final String extras;

   private ClientSession session;
   private ChannelShell shellChannel;
   private Consumer<Throwable> exceptionHandler;
   private ScpClient scpClient;
   private PrintStream commandStream;
   private BufferedReader reader;

   public SshDeployedAgent(String name, String runId, String username, String hostname, int port, String dir, String extras) {
      this.name = name;
      this.runId = runId;
      this.username = username;
      this.hostname = hostname;
      this.port = port;
      this.dir = dir;
      this.extras = extras;
   }

   @Override
   public void stop() {
      log.info("Stopping agent " + name);
      try {
         if (reader != null) {
            reader.close();
         }
      } catch (IOException e) {
         log.error("Failed closing output reader", e);
      }
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

      reader = new BufferedReader(new InputStreamReader(shellChannel.getInvertedOut()));
      commandStream = new PrintStream(shellChannel.getInvertedIn());
      runCommand("unset PROMPT_COMMAND; export PS1='" + PROMPT + "'", true);

      runCommand("mkdir -p " + dir + AGENTLIB, true);

      Map<String, String> remoteMd5 = getRemoteMd5();
      Map<String, String> localMd5 = getLocalMd5();
      if (localMd5 == null) {
         return;
      }

      StringBuilder startAgentCommmand = new StringBuilder("java -cp ");

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
      String log4jConfigurationFile = System.getProperty(Properties.LOG4J2_CONFIGURATION_FILE);
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
      startAgentCommmand.append(" -D").append(Properties.CONTROLLER_CLUSTER_IP).append('=').append(System.getProperty(Properties.CONTROLLER_CLUSTER_IP));
      startAgentCommmand.append(" -D").append(Properties.CONTROLLER_CLUSTER_PORT).append('=').append(System.getProperty(Properties.CONTROLLER_CLUSTER_PORT));
      if (DEBUG_ADDRESS != null) {
         startAgentCommmand.append(" -agentlib:jdwp=transport=dt_socket,server=y,suspend=").append(DEBUG_SUSPEND).append(",address=").append(DEBUG_ADDRESS);
      }
      if (extras != null) {
         startAgentCommmand.append(" ").append(extras);
      }
      startAgentCommmand.append(" io.hyperfoil.Hyperfoil\\$Agent &> ")
            .append(dir).append(File.separatorChar).append("agent.").append(name).append(".log");
      String startAgent = startAgentCommmand.toString();
      log.debug("Starting agent {}: {}", name, startAgent);
      runCommand(startAgent, false);
   }

   private List<String> runCommand(String cmd, boolean wait) {
      log.trace("Running command {}", cmd);
      commandStream.println(cmd);
      // add one more empty command so that we get PROMPT on the line alone
      commandStream.println();
      commandStream.flush();
      ArrayList<String> lines = new ArrayList<>();
      try {
         // first line should be echo of the command
         String ignored = reader.readLine();
         String line;
         if (!wait) {
            return null;
         }
         while ((line = reader.readLine()) != null && !PROMPT.equals(line)) {
            lines.add(line);
         }
      } catch (IOException e) {
         exceptionHandler.accept(new DeploymentException("Error reading from shell", e));
         return null;
      }
      return lines;
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
      List<String> lines = runCommand("md5sum " + dir + AGENTLIB + "/*", true);
      Map<String, String> md5map = new HashMap<>();
      for (String line : lines) {
         if (line.endsWith("No such file or directory")) {
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
