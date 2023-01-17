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

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.HdrHistogram.AbstractHistogram;
import org.HdrHistogram.HistogramIterationValue;
import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionGroup;
import org.aesh.command.option.OptionList;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCommandInvocationProvider;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.HistogramConverter;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.impl.Util;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;


public abstract class WrkAbstract {

   //ignore logging when running in the console below severe
   static {
      Handler[] handlers = Logger.getLogger("").getHandlers();
      for (int index = 0; index < handlers.length; index++) {
         handlers[index].setLevel(Level.SEVERE);
      }
   }

   protected abstract String getCommand();

   public int mainMethod(String[] args, Class<? extends AbstractWrkCommand> wrkClass) {
      CommandRuntime<HyperfoilCommandInvocation> cr = null;
      CommandResult result = null;
      try {
         AeshCommandRuntimeBuilder<HyperfoilCommandInvocation> runtime = AeshCommandRuntimeBuilder.builder();
         runtime.commandInvocationProvider(new HyperfoilCommandInvocationProvider(new HyperfoilCliContext()));
         @SuppressWarnings("unchecked")
         AeshCommandRegistryBuilder<HyperfoilCommandInvocation> registry =
               AeshCommandRegistryBuilder.<HyperfoilCommandInvocation>builder()
                     .commands(StartLocal.class, wrkClass, Exit.class);
         runtime.commandRegistry(registry.create());
         cr = runtime.build();
         try {
            cr.executeCommand("start-local --quiet");
            // As -H option could contain a whitespace we have to either escape the space or quote the argument.
            // However quoting would not work well if the argument contains a quote.
            String optionsCollected = Stream.of(args).map(arg -> arg.replaceAll(" ", "\\\\ ")).collect(Collectors.joining(" "));
            result = cr.executeCommand(getCommand() + " " + optionsCollected);
         } finally {
            cr.executeCommand("exit");
         }
      } catch (Exception e) {
         System.out.println("Failed to execute command: " + e.getMessage());
         if (Boolean.getBoolean("io.hyperfoil.stacktrace")) {
            e.printStackTrace();
         }
         if (cr != null) {
            try {
               System.out.println(cr.getCommandRegistry().getCommand(getCommand(), getCommand()).printHelp(getCommand()));
            } catch (CommandNotFoundException ex) {
               throw new IllegalStateException(ex);
            }
         }
         //todo: should provide help info here, will be added in newer version of æsh
      }
      return result == null ? CommandResult.FAILURE.getResultValue() : result.getResultValue();
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

      @Argument(description = "URL that should be accessed", required = true)
      String url;

      String path;
      String[][] parsedHeaders;

      @Override
      public CommandResult execute(HyperfoilCommandInvocation invocation) {
         if (help) {
            invocation.println(invocation.getHelpInfo(getCommand()));
            return CommandResult.SUCCESS;
         }
         if (script != null) {
            invocation.println("Scripting is not supported at this moment.");
         }
         if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
         }
         URI uri;
         try {
            uri = new URI(url);
         } catch (URISyntaxException e) {
            invocation.println("Failed to parse URL: " + e.getMessage());
            return CommandResult.FAILURE;
         }
         path = uri.getPath();
         if (path == null || path.isEmpty()) {
            path = "/";
         }
         if (uri.getQuery() != null) {
            path = path + "?" + uri.getQuery();
         }
         if (uri.getFragment() != null) {
            path = path + "#" + uri.getFragment();
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
               parsedHeaders[i] = new String[]{ header, value };
            }
         } else {
            parsedHeaders = null;
         }

         Protocol protocol = Protocol.fromScheme(uri.getScheme());
         // @formatter:off
         BenchmarkBuilder builder = BenchmarkBuilder.builder()
               .name(getCommand())
               .addPlugin(HttpPluginBuilder::new)
                  .ergonomics()
                     .repeatCookies(false)
                     .userAgentFromSession(false)
                  .endErgonomics()
                  .http()
                     .protocol(protocol).host(uri.getHost()).port(protocol.portOrDefault(uri.getPort()))
                     .allowHttp2(enableHttp2)
                     .sharedConnections(connections)
                  .endHttp()
               .endPlugin()
               .threads(this.threads);
         // @formatter:on
         if (agent != null) {
            for (Map.Entry<String, String> agent : agent.entrySet()) {
               Map<String, String> properties = Stream.of(agent.getValue().split(","))
                     .map(property -> {
                        String[] pair = property.split("=", 2);
                        if (pair.length != 2) {
                           throw new IllegalArgumentException("Cannot parse " + property + " as a property: Agent should be formatted as -AagentName=key1=value1,key2=value2...");
                        }
                        return pair;
                     })
                     .collect(Collectors.toMap(keyValue -> keyValue[0], keyValue -> keyValue[1]));
               builder.addAgent(agent.getKey(), null, properties);
            }
         }

         addPhase(builder, "calibration", "6s");
         // We can start only after calibration has full completed because otherwise some sessions
         // would not have connection available from the beginning.
         addPhase(builder, "test", duration).startAfterStrict("calibration").maxDuration(Util.parseToMillis(duration));

         RestClient client = invocation.context().client();
         if (client == null) {
            invocation.println("You're not connected to a controller; either " + ANSI.BOLD + "connect" + ANSI.BOLD_OFF
                  + " to running instance or use " + ANSI.BOLD + "start-local" + ANSI.BOLD_OFF
                  + " to start a controller in this VM");
            return CommandResult.FAILURE;
         }
         Client.BenchmarkRef benchmark = client.register(builder.build(), null);
         invocation.context().setServerBenchmark(benchmark);

         //validate benchmark
         Client.RunRef run = benchmark.start(null, Collections.emptyMap(), Boolean.TRUE);

         boolean result = awaitBenchmarkResult(run, invocation);

         if (result) {
            if (run.get().errors.size() > 0) {
               invocation.println("ERROR: " + run.get().errors.stream().collect(Collectors.joining(", ")));
               return CommandResult.FAILURE;
            }
         } else {
            return CommandResult.FAILURE;

         }

         run = benchmark.start(null, Collections.emptyMap());
         invocation.context().setServerRun(run);

         invocation.println("Running " + duration + " test @ " + url);
         invocation.println("  " + threads + " threads and " + connections + " connections");

         result = awaitBenchmarkResult(run, invocation);

         if (result) {
            RequestStatisticsResponse total = run.statsTotal();
            RequestStats testStats = total.statistics.stream().filter(rs -> "test".equals(rs.phase))
                  .findFirst().orElseThrow(() -> new IllegalStateException("Error running command: Missing Statistics"));
            AbstractHistogram histogram = HistogramConverter.convert(run.histogram(testStats.phase, testStats.stepId, testStats.metric));
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

      protected abstract PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog);


      private PhaseBuilder<?> addPhase(BenchmarkBuilder benchmarkBuilder, String phase, String durationStr) {
         // prevent capturing WrkCommand in closure
         String[][] parsedHeaders = this.parsedHeaders;
         long duration = Util.parseToMillis(durationStr);
         // @formatter:off
         return phaseConfig(benchmarkBuilder.addPhase(phase))
                 .duration(duration)
                 .maxDuration(duration + Util.parseToMillis(timeout))
                 .scenario()
                  .initialSequence("request")
                     .step(SC).httpRequest(HttpMethod.GET)
                        .path(path)
                        .headerAppender((session, request) -> {
                           if (parsedHeaders != null) {
                              for (String[] header : parsedHeaders) {
                                 request.putHeader(header[0], header[1]);
                              }
                           }
                        })
                        .timeout(timeout)
                        .handler()
                           .rawBytes(new TransferSizeRecorder("transfer"))
                        .endHandler()
                     .endStep()
                  .endSequence()
               .endScenario();
         // @formatter:on
      }

      private void printStats(StatisticsSummary stats, AbstractHistogram histogram, List<StatisticsSummary> series, CommandInvocation invocation) {
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
         double requestsStdDev = series.size() > 0 ? Math.sqrt(series.stream().mapToDouble(s -> Math.pow(s.requestCount - requestsStats.getAverage(), 2)).sum() / series.size()) : 0;
         invocation.println("    Req/Sec   " +
               String.format("%6.2f  ", requestsStats.getAverage()) +
               String.format("%8.2f  ", requestsStdDev) +
               String.format("%7.2f  ", requestsStats.getMax()) +
               String.format("%8.2f", statsWithinStdev(requestsStats, requestsStdDev, series.stream().mapToInt(s -> s.requestCount), series.size())));
         if (latency) {
            invocation.println("  Latency Distribution");
            for (double percentile : Arrays.asList(50.0, 75.0, 90.0, 99.0, 99.9, 99.99, 99.999, 100.0)) {
               invocation.println(String.format("    %7.3f%%", percentile) + " " + Util.prettyPrintNanos(histogram.getValueAtPercentile(percentile), "9", false));
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
         invocation.println("  " + stats.requestCount + " requests in " + durationSeconds + "s, " + Util.prettyPrintData(transferStats.sent + transferStats.received) + " read");
         invocation.println("Requests/sec: " + String.format("%.02f", stats.requestCount / durationSeconds));
         invocation.println("Transfer/sec: " + Util.prettyPrintData((transferStats.sent + transferStats.received) / durationSeconds));
         if (stats.connectionErrors + stats.requestTimeouts + stats.internalErrors > 0) {
            invocation.println("Socket errors: connectionErrors " + stats.connectionErrors + ", requestTimeouts " + stats.requestTimeouts);
         }
         if (httpStats.status_4xx + httpStats.status_5xx + httpStats.status_other > 0) {
            invocation.println("Non-2xx or 3xx responses: " + (httpStats.status_4xx + httpStats.status_5xx + httpStats.status_other));
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
         for (var it = histogram.allValues().iterator(); it.hasNext(); ) {
            HistogramIterationValue value = it.next();
            if (value.getValueIteratedFrom() >= lower && value.getValueIteratedTo() <= upper) {
               sum += value.getCountAddedInThisIterationStep();
            }
         }
         return 100d * sum / stats.requestCount;
      }

   }

}
