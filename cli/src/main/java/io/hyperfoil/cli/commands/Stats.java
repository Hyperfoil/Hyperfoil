package io.hyperfoil.cli.commands;

import java.util.Collection;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.utils.ANSI;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "stats", description = "Show run statistics")
public class Stats extends BaseRunIdCommand {
   private static final Table<Client.RequestStats> REQUEST_STATS_TABLE = new Table<Client.RequestStats>()
         .rowPrefix(r -> r.failedSLAs.isEmpty() ? null : ANSI.RED_TEXT)
         .rowSuffix(r -> ANSI.RESET)
         .column("PHASE", r -> r.phase)
         .column("METRIC", r -> r.metric)
         .columnInt("REQUESTS", r -> r.summary.requestCount)
         .columnNanos("MEAN", r -> r.summary.meanResponseTime)
         .columnNanos("p50", r -> r.summary.percentileResponseTime.get(50d))
         .columnNanos("p90", r -> r.summary.percentileResponseTime.get(90d))
         .columnNanos("p99", r -> r.summary.percentileResponseTime.get(99d))
         .columnNanos("p99.9", r -> r.summary.percentileResponseTime.get(99.9))
         .columnNanos("p99.99", r -> r.summary.percentileResponseTime.get(99.99))
         .columnInt("2xx", r -> r.summary.status_2xx)
         .columnInt("3xx", r -> r.summary.status_3xx)
         .columnInt("4xx", r -> r.summary.status_4xx)
         .columnInt("5xx", r -> r.summary.status_5xx)
         .columnInt("CACHE", r -> r.summary.cacheHits)
         .columnInt("TIMEOUTS", r -> r.summary.timeouts)
         .columnInt("ERRORS", r -> r.summary.resetCount + r.summary.connectFailureCount + r.summary.status_other)
         .columnNanos("BLOCKED", r -> r.summary.blockedTime);
   private static final Table<Client.CustomStats> CUSTOM_STATS_TABLE = new Table<Client.CustomStats>()
         .column("PHASE", c -> c.phase)
         .columnInt("STEP", c -> c.stepId)
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
         Client.RequestStatisticsResponse stats;
         try {
            stats = total || terminated ? runRef.statsTotal() : runRef.statsRecent();
         } catch (RestClientException e) {
            if (e.getCause() instanceof InterruptedException) {
               clearLines(invocation, 1);
               invocation.println("");
               return;
            }
            invocation.println("ERROR: " + Util.explainCauses(e));
            throw new CommandException("Cannot fetch stats for run " + runRef.id(), e);
         }
         if ("TERMINATED".equals(stats.status)) {
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
         invocation.println(REQUEST_STATS_TABLE.print(stats.statistics.stream()));
         prevLines = stats.statistics.size() + 2;
         if (terminated || interruptibleDelay(invocation)) {
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
