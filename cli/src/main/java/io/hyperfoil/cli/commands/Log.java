package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "log", description = "Browse remote logs.", aliases = "logs")
public class Log extends ServerCommand {
   @Option(name = "pager", shortName = 'p', description = "Pager used.")
   private String pager;

   @Argument(description = "Node with the log: controller/agent-name", completer = NodeCompleter.class)
   String node;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);

      File logFile = invocation.context().getLogFile(node);
      String logId = invocation.context().getLogId(node);
      long offset = 0;

      if (logFile == null) {
         try {
            File tmpFile = File.createTempFile(node == null ? "hfc." : node + ".", ".log");
            tmpFile.deleteOnExit();
            logFile = tmpFile;
         } catch (IOException e) {
            invocation.println("Cannot create temporary file for the log: " + Util.explainCauses(e));
            return CommandResult.FAILURE;
         }
      } else {
         try {
            offset = Files.size(logFile.toPath());
         } catch (IOException e) {
            invocation.println("Error fetching size of " + logFile + ": " + Util.explainCauses(e));
         }
      }

      String newLogId = invocation.context().client().downloadLog(node, logId, offset, logFile);
      // when the agent did not start correctly the newLogId will be null (as we use deployment ID for that)
      // and we won't store it at all.
      if (newLogId != null) {
         if (logId == null) {
            invocation.context().addLog(node, logFile, newLogId);
         } else if (!logId.equals(newLogId)) {
            invocation.context().updateLogId(node, newLogId);
         }
      }
      invocation.context().createPager(pager).open(invocation, logFile);
      if (newLogId == null) {
         logFile.delete();
      }
      return CommandResult.SUCCESS;
   }

   public static class NodeCompleter extends ServerOptionCompleter {
      public NodeCompleter() {
         super(client -> Stream.concat(Stream.of("controller"), client.agents().stream()));
      }
   }
}
