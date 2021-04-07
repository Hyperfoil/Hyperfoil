package io.hyperfoil.cli.commands;

import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "connections", aliases = "conns", description = "Shows number and type of connections.")
public class Connections extends BaseRunIdCommand {
   Table<Map.Entry<String, Client.MinMax>> CONNECTION_STATS = new Table<Map.Entry<String, Client.MinMax>>()
         .column("TYPE", Map.Entry::getKey)
         .column("MIN", e -> String.valueOf(e.getValue().min), Table.Align.RIGHT)
         .column("MAX", e -> String.valueOf(e.getValue().max), Table.Align.RIGHT);

   @Option(description = "Show overall stats for the run.")
   boolean total;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      Map<String, Map<String, Client.MinMax>> connectionStats = null;
      for (; ; ) {
         try {
            if (!total) {
               int numLines = connectionStats == null ? 0 : connectionStats.values().stream().mapToInt(Map::size).sum() + 2;
               connectionStats = runRef.connectionStatsRecent();
               clearLines(invocation, numLines);
            }
            if (connectionStats == null || connectionStats.isEmpty()) {
               var run = runRef.get();
               if (total || run.terminated != null) {
                  invocation.println("Run " + run.id + " has terminated.");
                  CONNECTION_STATS.print(invocation, "TARGET", CliUtil.toMapOfStreams(runRef.connectionStatsTotal()));
                  return CommandResult.SUCCESS;
               }
            }
            if (connectionStats != null) {
               CONNECTION_STATS.print(invocation, "TARGET", CliUtil.toMapOfStreams(connectionStats));
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
            throw new CommandException("Cannot display connection stats.", e);
         }
      }
   }
}
