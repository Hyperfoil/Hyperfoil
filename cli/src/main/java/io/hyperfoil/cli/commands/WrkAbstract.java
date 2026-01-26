/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramIterationValue;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.HistogramConverter;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.impl.Util;

public abstract class WrkAbstract extends BaseStandaloneCommand {

   public enum WrkVersion {
      V1,
      V2;
   }

   @Override
   protected List<Class<? extends Command<HyperfoilCommandInvocation>>> getDependencyCommands() {
      return List.of(Report.class);
   }

   //   @CommandDefinition(name = "wrk", description = "Runs a workload simulation against one endpoint using the same vm")
   public abstract class AbstractWrkCommand implements Command<HyperfoilCommandInvocation> {
      @Option(shortName = 'c', description = "Total number of HTTP connections to keep open", defaultValue = "10")
      int connections;

      @Option(shortName = 'd', description = "Duration of the test, e.g. 2s, 2m, 2h", defaultValue = "10s")
      String duration;

      @Option(shortName = 't', description = "Total number of threads to use.", defaultValue = "2")
      int threads;

      @Option(shortName = 's', description = "!!!NOT SUPPORTED: LuaJIT script")
      String script;

      @Option(shortName = 'h', hasValue = false, overrideRequired = true)
      boolean help;

      @OptionList(shortName = 'H', name = "header", description = "HTTP header to add to request, e.g. \"User-Agent: wrk\"")
      List<String> headers;

      @Option(description = "Print detailed latency statistics", hasValue = false)
      boolean latency;

      @Option(description = "Record a timeout if a response is not received within this amount of time.", defaultValue = "60s")
      String timeout;

      @OptionGroup(shortName = 'A', description = "Inline definition of agent executing the test. By default assuming non-clustered mode.")
      Map<String, String> agent;

      @Option(name = "enable-http2", description = "HTTP2 is not supported in wrk/wrk2: you can enable that for Hyperfoil.", defaultValue = "false")
      boolean enableHttp2;

      @Option(name = "use-http-cache", description = "By default the HTTP cache is disabled, providing this option you can enable it.", hasValue = false)
      boolean useHttpCache;

      @Argument(description = "URL that should be accessed", required = true)
      String url;

      @Option(name = "output", shortName = 'o', description = "Output destination path for the HTML report")
      private String output;

      @Option(name = "warmup-duration", description = "Duration of the warm up phase, e.g. 2s, 2m, 2h", defaultValue = "6s")
      String warmupDuration;

      String[][] parsedHeaders;
      boolean started = false;
      boolean initialized = false;

      @Override
      public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
         if (help) {
            invocation.println(invocation.getHelpInfo(getCommandName()));
            return CommandResult.SUCCESS;
         }
         if (script != null) {
            invocation.println("Scripting is not supported at this moment.");
         }

         if (headers != null) {
            parsedHeaders = new String[headers.size()][];
            for (int i = 0; i < headers.size(); i++) {
               String h = headers.get(i);
               int colonIndex = h.indexOf(':');
               if (colonIndex < 0) {
                  invocation.println(String.format("Cannot parse header '%s', ignoring.", h));
                  continue;
               }
               String header = h.substring(0, colonIndex).trim();
               String value = h.substring(colonIndex + 1).trim();
               parsedHeaders[i] = new String[] { header, value };
            }
         } else {
            parsedHeaders = null;
         }

         // @formatter:off
         WrkScenario scenario = new WrkScenario() {
            @Override protected PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType, long durationMs) {
               return AbstractWrkCommand.this.phaseConfig(catalog, phaseType, durationMs);
            }
         };

         BenchmarkBuilder builder;
         try {
            if (WrkVersion.V1.equals(this.getWrkVersion())) {
               builder = scenario.getWrkBenchmark(getCommandName(), url, enableHttp2, connections, useHttpCache,
                     threads, agent, warmupDuration, duration, parsedHeaders, timeout);
            } else if (WrkVersion.V2.equals(this.getWrkVersion())) {
               builder = scenario.getWrk2Benchmark(getCommandName(), url, enableHttp2, connections, useHttpCache,
                     threads, agent, warmupDuration, duration, parsedHeaders, timeout);
            } else {
               throw new IllegalArgumentException("Unknown WrkVersion: " + this.getWrkVersion());
            }

         } catch (URISyntaxException e) {
            invocation.println("Failed to parse URL: " + e.getMessage());
            return CommandResult.FAILURE;
         }

         RestClient client = invocation.context().client();
         if (client == null) {
            invocation.println("You're not connected to a controller; either " + ANSI.BOLD + "connect" + ANSI.BOLD_OFF
                  + " to running instance or use " + ANSI.BOLD + "start-local" + ANSI.BOLD_OFF
                  + " to start a controller in this VM");
            return CommandResult.FAILURE;
         }
         Client.BenchmarkRef benchmark = client.register(builder.build(), null);
         invocation.context().setServerBenchmark(benchmark);

         Client.RunRef run = benchmark.start(null, Collections.emptyMap());
         invocation.context().setServerRun(run);

         boolean result = awaitBenchmarkResult(run, invocation);

         if (result) {
            if (output != null && !output.isBlank()) {
               invocation.executeSwitchable("report --silent -y --destination " + output);
            }
            if (!started && !run.get().errors.isEmpty()) {
               // here the benchmark simulation did not start, it failed during initialization
               // print the error that is returned by the controller, e.g., cannot connect
               invocation.println("ERROR: " + String.join(", ", run.get().errors));
               return CommandResult.FAILURE;
            }
            RequestStatisticsResponse total = run.statsTotal();
            RequestStats testStats = null;
            List<String> phases = new ArrayList<>();
            for (RequestStats rs : total.statistics) {
               if (WrkScenario.PhaseType.test.name().equals(rs.phase)) {
                  testStats = rs;
                  break;
               } else {
                  phases.add(rs.phase);
               }
            }
            if (testStats == null) {
               invocation.println("Error: Missing Statistics for '" + WrkScenario.PhaseType.test.name() + "'. Found only for: "
                     + String.join(", ", phases));
               return CommandResult.FAILURE;
            }
            AbstractHistogram histogram = HistogramConverter
                  .convert(run.histogram(testStats.phase, testStats.stepId, testStats.metric));
            List<StatisticsSummary> series = run.series(testStats.phase, testStats.stepId, testStats.metric);
            printStats(testStats.summary, histogram, series, invocation);
            return CommandResult.SUCCESS;
         } else {
            return CommandResult.FAILURE;
         }
      }

      private boolean awaitBenchmarkResult(Client.RunRef run, HyperfoilCommandInvocation invocation) {
         while (true) {
            RequestStatisticsResponse recent = run.statsRecent();
            if ("TERMINATED".equals(recent.status)) {
               break;
            } else if ("INITIALIZING".equals(recent.status) && !initialized) {
               initialized = true;
            } else if ("RUNNING".equals(recent.status) && !started) {
               // here the benchmark simulation started, so we can print wrk/wrk2 messages
               invocation.println("Running " + duration + " test @ " + url);
               invocation.println("  " + threads + " threads and " + connections + " connections");
               started = true;
               // if started, it is also initialized
               // this ensure initialized is set to true if for some reason we did not catch the "INITIALIZING" status
               initialized = true;
            }

            invocation.getShell().write(ANSI.CURSOR_START);
            invocation.getShell().write(ANSI.ERASE_WHOLE_LINE);
            try {
               Thread.sleep(1000);
            } catch (InterruptedException e) {
               invocation.println("Interrupt received, trying to abort run...");
               run.kill();
               return false;
            }
         }

         return true;
      }

      protected abstract PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, WrkScenario.PhaseType phaseType, long durationMs);

      protected abstract WrkVersion getWrkVersion();

      private void printStats(StatisticsSummary stats, AbstractHistogram histogram, List<StatisticsSummary> series,
            CommandInvocation invocation) {
         TransferSizeRecorder.Stats transferStats = (TransferSizeRecorder.Stats) stats.extensions.get("transfer");
         HttpStats httpStats = HttpStats.get(stats);
         double durationSeconds = (stats.endTime - stats.startTime) / 1000d;
         invocation.println(String.format("  Thread Stats%6s%11s%8s%12s", "Avg", "Stdev", "Max", "+/- Stdev"));
         invocation.println("    Latency   " +
               Util.prettyPrintNanos(stats.meanResponseTime, "6", false) +
               Util.prettyPrintNanos((long) histogram.getStdDeviation(), "8", false) +
               Util.prettyPrintNanos(stats.maxResponseTime, "7", false) +
               String.format("%8.2f%%", statsWithinStdev(stats, histogram)));
         // Note: wrk samples #requests every 100 ms, Hyperfoil every 1s
         DoubleSummaryStatistics requestsStats = series.stream().mapToDouble(s -> s.requestCount).summaryStatistics();
         double requestsStdDev = !series.isEmpty() ? Math.sqrt(
               series.stream().mapToDouble(s -> Math.pow(s.requestCount - requestsStats.getAverage(), 2)).sum() / series.size())
               : 0;
         invocation.println("    Req/Sec   " +
               String.format("%6.2f  ", requestsStats.getAverage()) +
               String.format("%8.2f  ", requestsStdDev) +
               String.format("%7.2f  ", requestsStats.getMax()) +
               String.format("%8.2f", statsWithinStdev(requestsStats, requestsStdDev,
                     series.stream().mapToInt(s -> s.requestCount), series.size())));
         if (latency) {
            invocation.println("  Latency Distribution");
            for (double percentile : Arrays.asList(50.0, 75.0, 90.0, 99.0, 99.9, 99.99, 99.999, 100.0)) {
               invocation.println(String.format("    %7.3f%%", percentile) + " "
                     + Util.prettyPrintNanos(histogram.getValueAtPercentile(percentile), "9", false));
            }
            invocation.println("");
            invocation.println("  Detailed Percentile Spectrum");
            histogram.outputPercentileDistribution(new PrintStream(new OutputStream() {
               @Override
               public void write(int b) throws IOException {
                  invocation.print(String.valueOf((char) b));
               }
            }), 5, 1000_000.0);
            invocation.println("----------------------------------------------------------");
         }
         invocation.println("  " + stats.requestCount + " requests in " + durationSeconds + "s, "
               + Util.prettyPrintData(transferStats.sent + transferStats.received) + " read");
         invocation.println("Requests/sec: " + String.format("%.02f", stats.requestCount / durationSeconds));
         invocation.println(
               "Transfer/sec: " + Util.prettyPrintData((transferStats.sent + transferStats.received) / durationSeconds));
         if (stats.connectionErrors + stats.requestTimeouts + stats.internalErrors > 0) {
            invocation.println(
                  "Socket errors: connectionErrors " + stats.connectionErrors + ", requestTimeouts " + stats.requestTimeouts);
         }
         if (httpStats.status_4xx + httpStats.status_5xx + httpStats.status_other > 0) {
            invocation.println(
                  "Non-2xx or 3xx responses: " + (httpStats.status_4xx + httpStats.status_5xx + httpStats.status_other));
         }
      }

      private double statsWithinStdev(DoubleSummaryStatistics stats, double stdDev, IntStream stream, int count) {
         double lower = stats.getAverage() - stdDev;
         double upper = stats.getAverage() + stdDev;
         return 100d * stream.filter(reqs -> reqs >= lower && reqs <= upper).count() / count;
      }

      private double statsWithinStdev(StatisticsSummary stats, AbstractHistogram histogram) {
         double stdDev = histogram.getStdDeviation();
         double lower = stats.meanResponseTime - stdDev;
         double upper = stats.meanResponseTime + stdDev;
         long sum = 0;
         for (var it = histogram.allValues().iterator(); it.hasNext();) {
            HistogramIterationValue value = it.next();
            if (value.getValueIteratedFrom() >= lower && value.getValueIteratedTo() <= upper) {
               sum += value.getCountAddedInThisIterationStep();
            }
         }
         return 100d * sum / stats.requestCount;
      }

   }

}
