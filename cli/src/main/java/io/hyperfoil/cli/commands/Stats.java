package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;

@CommandDefinition(name = "stats", description = "Show run statistics")
public class Stats extends BaseRunIdCommand {

   @Option(name = "total", shortName = 't', description = "Show total stats instead of recent.", hasValue = false)
   private boolean total;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      boolean terminated = false;
      for (;;) {
         String stats;
         try {
            stats = total || terminated ? runRef.statsTotal() : runRef.statsRecent();
         } catch (RestClientException e) {
            throw new CommandException("Cannot fetch stats for run " + runRef.id(), e);
         }
         int lines = numLines(stats);
         if (lines == 1) {
            // There are no (recent) stats, the run has probably terminated
            stats = runRef.statsTotal();
            invocation.println("Total stats from run " + runRef.id());
            invocation.print(stats);
            return CommandResult.SUCCESS;
         } else {
            if (total) {
               invocation.println("Total stats from run " + runRef.id());
            } else {
               invocation.println("Recent stats from run " + runRef.id());
            }
            invocation.print(stats);
            if (terminated) {
               return CommandResult.SUCCESS;
            }
            try {
               if (runRef.get().terminated != null) {
                  terminated = true;
               }
            } catch (RestClientException e) {
               return CommandResult.FAILURE;
            }
            invocation.println("Press Ctr+C to stop watching...");
            if (!terminated) {
               Thread.sleep(1000);
            }
            clearLines(invocation, lines + 2);
         }
      }
   }

   private int numLines(String string) {
      int lines = 0;
      for (int i = 0; i < string.length(); ++i) {
         if (string.charAt(i) == '\n') {
            ++lines;
         }
      }
      return lines;
   }
}
