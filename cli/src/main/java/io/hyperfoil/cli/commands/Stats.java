package io.hyperfoil.cli.commands;

import java.util.Collection;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "stats", description = "Show run statistics")
public class Stats extends BaseRunIdCommand {
   private static final Table<Client.CustomStats> CUSTOM_STATS_TABLE = new Table<Client.CustomStats>()
         .column("PHASE", c -> c.phase)
         .column("STEP", c -> String.valueOf(c.stepId))
         .column("METRIC", c -> c.metric)
         .column("NAME", c -> c.customName)
         .column("VALUE", c -> c.value);

   @Option(name = "total", shortName = 't', description = "Show total stats instead of recent.", hasValue = false)
   private boolean total;

   @Option(name = "custom", shortName = 'c', description = "Show custom stats (total only)", hasValue = false)
   private boolean custom;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      if (custom) {
         showCustomStats(invocation, runRef);
      } else {
         showStats(invocation, runRef);
      }
      return CommandResult.SUCCESS;
   }

   private void showStats(HyperfoilCommandInvocation invocation, Client.RunRef runRef) throws CommandException {
      boolean terminated = false;
      int prevLines = -2;
      for (;;) {
         String stats;
         try {
            stats = total || terminated ? runRef.statsTotal() : runRef.statsRecent();
         } catch (RestClientException e) {
            invocation.println("ERROR: " + Util.explainCauses(e));
            throw new CommandException("Cannot fetch stats for run " + runRef.id(), e);
         }
         int lines = numLines(stats);
         if (lines == 1) {
            // There are no (recent) stats, the run has probably terminated
            stats = runRef.statsTotal();
            terminated = true;
         }
         clearLines(invocation, prevLines + 2);
         if (total || terminated) {
            invocation.println("Total stats from run " + runRef.id());
         } else {
            invocation.println("Recent stats from run " + runRef.id());
         }
         invocation.print(stats);
         prevLines = lines;
         if (terminated || interruptibleDelay(invocation)) {
            return;
         }
         try {
            if (runRef.get().terminated != null) {
               terminated = true;
            }
         } catch (RestClientException e) {
            invocation.println("ERROR: " + Util.explainCauses(e));
            return;
         }
      }
   }

   private void showCustomStats(HyperfoilCommandInvocation invocation, Client.RunRef runRef) throws CommandException {
      try {
         Collection<Client.CustomStats> customStats = runRef.customStats();
         invocation.println(CUSTOM_STATS_TABLE.print(customStats.stream()));
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Cannot fetch custom stats for run " + runRef.id(), e);
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
