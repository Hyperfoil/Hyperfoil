package io.hyperfoil.cli.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;

@CommandDefinition(name = "compare", description = "Compare results from two runs")
public class Compare extends ServerCommand {
   private final Table<Comparison> TABLE = new Table<Comparison>()
         .column("PHASE", c -> c.phase)
         .column("METRIC", c -> c.metric)
         .column("REQUESTS", c -> compare(c, ss -> ss.requestCount), Table.Align.RIGHT)
         .column("MEAN", c -> compareNanos(c, ss -> ss.meanResponseTime), Table.Align.RIGHT)
         .column("p50", c -> compareNanos(c, ss -> ss.percentileResponseTime.get(50d)), Table.Align.RIGHT)
         .column("p90", c -> compareNanos(c, ss -> ss.percentileResponseTime.get(90d)), Table.Align.RIGHT)
         .column("p99", c -> compareNanos(c, ss -> ss.percentileResponseTime.get(99d)), Table.Align.RIGHT)
         .column("p99.9", c -> compareNanos(c, ss -> ss.percentileResponseTime.get(99.9)), Table.Align.RIGHT)
         .column("p99.99", c -> compareNanos(c, ss -> ss.percentileResponseTime.get(99.99)), Table.Align.RIGHT);

   @Arguments(required = true, description = "Runs that should be compared.", completer = RunCompleter.class)
   private List<String> runIds;

   @Option(name = "threshold", shortName = '\t', description = "Difference threshold for coloring.", defaultValue = "0.05")
   private double threshold;

   @Option(shortName = 'w', description = "Include statistics from warm-up phases.", hasValue = false)
   private boolean warmup;

   private String compare(Comparison c, ToIntFunction<StatisticsSummary> f) {
      if (c.first == null || c.second == null) {
         return "N/A";
      }
      int first = f.applyAsInt(c.first);
      int second = f.applyAsInt(c.second);
      StringBuilder sb = new StringBuilder();
      double diff = (double) (second - first) / Math.min(first, second);
      if (diff > threshold || diff < -threshold) {
         sb.append(ANSI.YELLOW_TEXT);
      }
      sb.append(String.format("%+d(%+.2f%%)", second - first, diff * 100));
      sb.append(ANSI.RESET);
      return sb.toString();
   }

   private String compareNanos(Comparison c, ToLongFunction<StatisticsSummary> f) {
      if (c.first == null || c.second == null) {
         return "N/A";
      }
      long first = f.applyAsLong(c.first);
      long second = f.applyAsLong(c.second);
      StringBuilder sb = new StringBuilder();
      double diff = (double) (second - first) / Math.min(first, second);
      if (diff > threshold) {
         sb.append(ANSI.RED_TEXT);
      } else if (diff < -threshold) {
         sb.append(ANSI.GREEN_TEXT);
      }
      sb.append(prettyPrintNanosDiff(second - first));
      sb.append(String.format("(%+.2f%%)", diff * 100));
      sb.append(ANSI.RESET);
      return sb.toString();
   }

   public static String prettyPrintNanosDiff(long meanResponseTime) {
      if (meanResponseTime < 1000 && meanResponseTime > -1000) {
         return String.format("%+6d ns", meanResponseTime);
      } else if (meanResponseTime < 1000_000 && meanResponseTime > -1000_000) {
         return String.format("%+6.2f μs", meanResponseTime / 1000d);
      } else if (meanResponseTime < 1000_000_000 && meanResponseTime > -1000_000_000) {
         return String.format("%+6.2f ms", meanResponseTime / 1000_000d);
      } else {
         return String.format("%+6.2f s ", meanResponseTime / 1000_000_000d);
      }
   }

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      if (runIds.size() < 2) {
         invocation.println("Two run IDs required for comparison.");
         return CommandResult.FAILURE;
      } else if (runIds.size() > 2) {
         invocation.println("This command can compare only two run IDs; ignoring others.");
      }

      Client.RunRef firstRun = ensureComplete(invocation, runIds.get(0));
      Client.RunRef secondRun = ensureComplete(invocation, runIds.get(1));
      RequestStatisticsResponse firstStats = firstRun.statsTotal();
      RequestStatisticsResponse secondStats = secondRun.statsTotal();
      invocation.println("Comparing runs " + firstRun.id() + " and " + secondRun.id());

      List<Comparison> comparisons = new ArrayList<>();
      for (RequestStats stats : firstStats.statistics) {
         if (stats.isWarmup && !warmup) continue;
         comparisons.add(new Comparison(stats.phase, stats.metric).first(stats.summary));
      }
      for (RequestStats stats : secondStats.statistics) {
         if (stats.isWarmup && !warmup) continue;
         Optional<Comparison> maybeComparison = comparisons.stream()
               .filter(c -> c.phase.equals(stats.phase) && c.metric.equals(stats.metric)).findAny();
         if (maybeComparison.isPresent()) {
            maybeComparison.get().second = stats.summary;
         } else {
            comparisons.add(new Comparison(stats.phase, stats.metric).second(stats.summary));
         }
      }
      TABLE.print(invocation, comparisons.stream());
      return CommandResult.SUCCESS;
   }

   private Client.RunRef ensureComplete(HyperfoilCommandInvocation invocation, String runId) throws CommandException {
      Client.RunRef firstRun = invocation.context().client().run(runId);
      if (firstRun.get().terminated == null) {
         throw new CommandException("Run " + firstRun.id() + " did not complete yet.");
      }
      return firstRun;
   }

   private static class Comparison {
      final String phase;
      final String metric;
      StatisticsSummary first;
      StatisticsSummary second;

      Comparison(String phase, String metric) {
         this.phase = phase;
         this.metric = metric;
      }

      public Comparison first(StatisticsSummary first) {
         this.first = first;
         return this;
      }

      public Comparison second(StatisticsSummary second) {
         this.second = second;
         return this;
      }
   }
}
