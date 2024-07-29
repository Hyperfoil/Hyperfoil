package io.hyperfoil.cli.commands;

import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "sessions", description = "Show sessions statistics")
public class Sessions extends BaseRunIdCommand {
   Table<Map.Entry<String, Client.MinMax>> SESSION_STATS = new Table<Map.Entry<String, Client.MinMax>>()
         .column("AGENT", Map.Entry::getKey)
         .column("MIN", e -> String.valueOf(e.getValue().min), Table.Align.RIGHT)
         .column("MAX", e -> String.valueOf(e.getValue().max), Table.Align.RIGHT);

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      Map<String, Map<String, Client.MinMax>> sessionStats = null;
      for (;;) {
         try {
            int numLines = sessionStats == null ? 0 : sessionStats.values().stream().mapToInt(Map::size).sum() + 2;
            sessionStats = runRef.sessionStatsRecent();
            clearLines(invocation, numLines);
            if (sessionStats == null || sessionStats.isEmpty()) {
               io.hyperfoil.controller.model.Run run = runRef.get();
               if (run.terminated != null) {
                  invocation.println("Run " + run.id + " has terminated.");
                  SESSION_STATS.print(invocation, "PHASE", CliUtil.toMapOfStreams(runRef.sessionStatsTotal()));
                  return CommandResult.SUCCESS;
               }
            }
            if (sessionStats != null) {
               SESSION_STATS.print(invocation, "PHASE", CliUtil.toMapOfStreams(sessionStats));
            }
            if (interruptibleDelay(invocation)) {
               return CommandResult.SUCCESS;
            }
         } catch (RestClientException e) {
            if (e.getCause() instanceof InterruptedException) {
               clearLines(invocation, 1);
               invocation.println("");
               return CommandResult.SUCCESS;
            }
            invocation.error(e);
            throw new CommandException("Cannot display session stats.", e);
         }
      }
   }

}
