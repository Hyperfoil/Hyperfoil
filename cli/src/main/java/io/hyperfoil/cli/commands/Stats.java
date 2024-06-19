package io.hyperfoil.cli.commands;

import java.util.Map;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.api.statistics.StatsExtension;
import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.http.statistics.HttpStats;

@CommandDefinition(name = "stats", description = "Show run statistics")
public class Stats extends BaseRunIdCommand {
   private static final Table<RequestStats> REQUEST_STATS_TABLE = new Table<RequestStats>()
         .idColumns(2)
         .rowPrefix(r -> r.failedSLAs.isEmpty() ? null : ANSI.RED_TEXT)
         .rowSuffix(r -> ANSI.RESET)
         .column("PHASE", r -> r.phase)
         .column("METRIC", r -> r.metric)
         .column("THROUGHPUT", Stats::throughput, Table.Align.RIGHT)
         .columnInt("REQUESTS", r -> r.summary.requestCount)
         .columnNanos("MEAN", r -> r.summary.meanResponseTime)
         .columnNanos("STD_DEV", r -> r.summary.stdDevResponseTime)
         .columnNanos("p50", r -> r.summary.percentileResponseTime.get(50d))
         .columnNanos("p90", r -> r.summary.percentileResponseTime.get(90d))
         .columnNanos("p99", r -> r.summary.percentileResponseTime.get(99d))
         .columnNanos("p99.9", r -> r.summary.percentileResponseTime.get(99.9))
         .columnNanos("p99.99", r -> r.summary.percentileResponseTime.get(99.99))
         .columnInt("TIMEOUTS", r -> r.summary.requestTimeouts)
         .columnInt("ERRORS", r -> r.summary.connectionErrors + r.summary.internalErrors)
         .columnNanos("BLOCKED", r -> r.summary.blockedTime);

   private static final String[] DIRECT_EXTENSIONS = { HttpStats.HTTP };

   @Option(shortName = 't', description = "Show total stats instead of recent.", hasValue = false)
   private boolean total;

   @Option(shortName = 'e', description = "Show extensions for given key. Use 'all' or '*' to show all extensions not shown by default, or comma-separated list.", completer = ExtensionsCompleter.class)
   private String extensions;

   @Option(shortName = 'w', description = "Include statistics from warmup phases.", hasValue = false)
   private boolean warmup;

   private static String throughput(RequestStats r) {
      if (r.summary.endTime <= r.summary.startTime) {
         return "<none>";
      } else {
         double rate = 1000d * r.summary.responseCount / (r.summary.endTime - r.summary.startTime);
         if (rate < 10_000) {
            return String.format("%.2f req/s", rate);
         } else if (rate < 10_000_000) {
            return String.format("%.2fk req/s", rate / 1000);
         } else {
            return String.format("%.2fM req/s", rate / 1000_000);
         }
      }
   }

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      boolean terminated = false;
      int prevLines = -2;
      for (; ; ) {
         RequestStatisticsResponse stats;
         try {
            stats = total ? runRef.statsTotal() : runRef.statsRecent();
         } catch (RestClientException e) {
            if (e.getCause() instanceof InterruptedException) {
               clearLines(invocation, 1);
               invocation.println("");
               break;
            }
            invocation.error(e);
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
         if (extensions == null || extensions.isEmpty()) {
            prevLines = showGeneralStats(invocation, stats);
         } else {
            prevLines = showExtensions(invocation, stats);
         }
         if (terminated || interruptibleDelay(invocation)) {
            break;
         }
      }
      return CommandResult.SUCCESS;
   }

   private int showGeneralStats(HyperfoilCommandInvocation invocation, RequestStatisticsResponse stats) {
      int prevLines = 0;
      String[] extensions = extensions(stats).toArray(String[]::new);
      if (extensions.length > 0) {
         invocation.print("Extensions (use -e to show): ");
         invocation.println(String.join(", ", extensions));
         prevLines++;
      }
      Table<RequestStats> table = new Table<>(REQUEST_STATS_TABLE);
      addDirectExtensions(stats, table);
      prevLines += table.print(invocation, stream(stats));
      for (RequestStats rs : stats.statistics) {
         if (rs.isWarmup && !warmup) continue;
         for (String msg : rs.failedSLAs) {
            invocation.println(String.format("%s/%s: %s", rs.phase, rs.metric == null ? "*" : rs.metric, msg));
            prevLines++;
         }
      }
      return prevLines;
   }

   private Stream<RequestStats> stream(RequestStatisticsResponse stats) {
      Stream<RequestStats> stream = stats.statistics.stream();
      if (!warmup) {
         stream = stream.filter(rs -> !rs.isWarmup);
      }
      return stream;
   }

   private int showExtensions(HyperfoilCommandInvocation invocation, RequestStatisticsResponse stats) {
      Table<RequestStats> table = new Table<RequestStats>().idColumns(2);
      table.column("PHASE", r -> r.phase).column("METRIC", r -> r.metric);
      if (extensions.equalsIgnoreCase("all") || extensions.equals("*")) {
         extensions(stats).flatMap(ext -> stream(stats).flatMap(rs -> {
            StatsExtension extension = rs.summary.extensions.get(ext);
            return extension == null ? Stream.empty() : Stream.of(extension.headers()).map(h -> Map.entry(ext, h));
         })).distinct().forEach(extHeader ->
               table.column(extHeader.getKey() + "." + extHeader.getValue(),
                     rs -> rs.summary.extensions.get(extHeader.getKey()).byHeader(extHeader.getValue()), Table.Align.RIGHT)
         );
      } else if (!extensions.contains(",")) {
         stream(stats).flatMap(rs -> {
            StatsExtension extension = rs.summary.extensions.get(extensions);
            return extension == null ? Stream.empty() : Stream.of(extension.headers());
         }).distinct().forEach(header ->
               table.column(header, rs -> rs.summary.extensions.get(extensions).byHeader(header), Table.Align.RIGHT));
      } else {
         String[] exts = extensions.split(",");
         stream(stats).flatMap(rs -> Stream.of(exts).flatMap(ext -> {
            StatsExtension extension = rs.summary.extensions.get(ext);
            return extension == null ? Stream.empty() : Stream.of(extension.headers()).map(h -> Map.entry(ext, h));
         })).distinct().forEach(extHeader ->
               table.column(extHeader.getKey() + "." + extHeader.getValue(),
                     rs -> rs.summary.extensions.get(extHeader.getKey()).byHeader(extHeader.getValue()), Table.Align.RIGHT)
         );
      }
      return table.print(invocation, stream(stats));
   }

   private static Stream<String> extensions(RequestStatisticsResponse stats) {
      return stats.statistics.stream().flatMap(rs -> rs.summary.extensions.keySet().stream())
            .sorted().distinct().filter(ext -> Stream.of(DIRECT_EXTENSIONS).noneMatch(de -> de.equals(ext)));
   }

   private void addDirectExtensions(RequestStatisticsResponse stats, Table<RequestStats> table) {
      boolean hasHttp = stream(stats).anyMatch(rs -> rs.summary.extensions.containsKey(HttpStats.HTTP));
      if (hasHttp) {
         table.columnInt("2xx", r -> HttpStats.get(r.summary).status_2xx)
               .columnInt("3xx", r -> HttpStats.get(r.summary).status_3xx)
               .columnInt("4xx", r -> HttpStats.get(r.summary).status_4xx)
               .columnInt("5xx", r -> HttpStats.get(r.summary).status_5xx)
               .columnInt("CACHE", r -> HttpStats.get(r.summary).cacheHits);
      }
   }

   public static class ExtensionsCompleter extends HyperfoilOptionCompleter {
      public ExtensionsCompleter() {
         super(context -> {
            if (context.serverRun() == null) {
               return Stream.empty();
            }
            return Stream.concat(extensions(context.serverRun().statsTotal()), Stream.of("all"));
         });
      }
   }
}
