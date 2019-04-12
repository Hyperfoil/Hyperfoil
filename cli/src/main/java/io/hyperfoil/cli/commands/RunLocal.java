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

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.CustomValue;
import io.hyperfoil.api.statistics.LongValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.io.Resource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@CommandDefinition(name = "run-local", description = "read-yaml command to initiate a hyperfoil workload through a yaml file")
public class RunLocal implements Command<CommandInvocation> {

    //ignore logging when running in the console below severe
    static {
        Handler[] handlers =
                Logger.getLogger( "" ).getHandlers();
        for ( int index = 0; index < handlers.length; index++ ) {
            handlers[index].setLevel( Level.SEVERE);
        }
    }


    @Option(shortName = 'h', hasValue = false, overrideRequired = true)
    boolean help;

    @Argument(description = "Yaml file that should be parsed", required = true)
    Resource yaml;

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) {
        if(help) {
            commandInvocation.println(commandInvocation.getHelpInfo("run-local"));
            return CommandResult.SUCCESS;
        }

        try {
            Benchmark benchmark = buildBenchmark(yaml.read(), commandInvocation);

            if(benchmark != null) {
                LocalSimulationRunner runner = new LocalSimulationRunner(benchmark, (phase, stepId, name, stats, countDown) -> printStats(phase, name, stats, commandInvocation), null);
                commandInvocation.println("Running benchmark '" + benchmark.name() + "'");
                commandInvocation.println("Using " + benchmark.threads() + " thread(s)");
                commandInvocation.print("Target servers: ");
                commandInvocation.println(String.join(", ", benchmark.http().values().stream().map(http -> http.baseUrl() + " (" + http.sharedConnections() + " connections)").collect(Collectors.toList())));
                runner.run();
            }
        }
        catch(FileNotFoundException e){
            commandInvocation.println("Couldn't find benchmark file: " + e.getMessage());
        }
        return CommandResult.SUCCESS;
    }

    private Benchmark buildBenchmark(InputStream inputStream, CommandInvocation invocation){
        if (inputStream == null)
            invocation.println("Could not find benchmark configuration");

        try {
            String source = Util.toString(inputStream);
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, new LocalBenchmarkData());

            if(benchmark == null)
                invocation.println("Failed to parse benchmark configuration");

            return benchmark;
        }
        catch (ParserException | IOException e) {
            invocation.println("Error occurred during parsing: "+e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private void printStats(Phase phase, String name, StatisticsSnapshot stats, CommandInvocation invocation) {
        if (stats.requestCount == 0) {
            return;
        }

        invocation.println("Statistics for " + phase.name() + "/" + name + ":");

        double durationSeconds = (stats.histogram.getEndTimeStamp() - stats.histogram.getStartTimeStamp()) / 1000d;
        invocation.print(stats.histogram.getTotalCount() + " requests in " + durationSeconds + "s");

        CustomValue bytes = stats.custom.get("bytes");
        String transferPerSec = null;
        if (bytes != null && bytes instanceof LongValue) {
            long numBytes = ((LongValue) bytes).value();
            transferPerSec = Util.prettyPrintData(numBytes / durationSeconds);
            invocation.println(", "+ Util.prettyPrintData(numBytes) +" read");
        } else {
            invocation.println("");
        }

        invocation.println("                 Avg    Stdev      Max");
        invocation.println("Latency:      "+ Util.prettyPrintNanos((long) stats.histogram.getMean())+" "
              +Util.prettyPrintNanos((long)stats.histogram.getStdDeviation())+" "
              +Util.prettyPrintNanos(stats.histogram.getMaxValue()));
         /*
         if (latency) {
            invocation.println("Latency Distribution");
            for (double percentile : new double[] { 0.5, 0.75, 0.9, 0.99, 0.999, 0.9999, 0.99999, 1.0}) {
               invocation.println(String.format("%7.3f", 100 * percentile)+" "+Util.prettyPrintNanos((long)stats.histogram.getValueAtPercentile(100 * percentile)));
            }
            invocation.println("----------------------------------------------------------");
            invocation.println("Detailed Percentile Spectrum");
            invocation.println("   Value  Percentile  TotalCount  1/(1-Percentile)");
            for (HistogramIterationValue value : stats.histogram.percentiles(5)) {
               invocation.println(Util.prettyPrintNanos((long)value.getValueIteratedTo())+" "+String.format("%9.5f%%  %10d  %15.2f",
                     value.getPercentile(), value.getTotalCountToThisValue(), 100/(100 - value.getPercentile())));
            }
            invocation.println("----------------------------------------------------------");
         }
         */
        invocation.println("Requests/sec: "+stats.histogram.getTotalCount() / durationSeconds);
        if (stats.errors() > 0) {
            invocation.println("Socket errors: connect "+stats.connectFailureCount+", reset "+stats.resetCount+", timeout "+stats.timeouts);
            invocation.println("Non-2xx or 3xx responses: "+ (stats.status_4xx + stats.status_5xx + stats.status_other));
        }
        if (transferPerSec != null) {
            invocation.println("Transfer/sec: " + transferPerSec);
        }
        invocation.println("---");
    }

    public static void main(String[] args) throws Exception {
        CommandRuntime runtime =
                AeshCommandRuntimeBuilder.builder()
                                         .commandRegistry(AeshCommandRegistryBuilder.builder()
                                                                  .command(RunLocal.class).create())
                                         .build();

        StringBuilder sb = new StringBuilder("main ");
        if (args.length == 1) {
            // When executed from mvn exec:exec -Pmain -Dmain.args="..." we don't want to quote the args
            sb.append(args[0]);
        } else {
            for (String arg : args) {
                if (arg.indexOf(' ') >= 0) {
                    sb.append('"').append(arg).append("\" ");
                } else {
                    sb.append(arg).append(' ');
                }
            }
        }
        try {
            runtime.executeCommand(sb.toString());
        }
        catch (Exception e) {
            System.out.println("Failed to execute command:"+ e.getMessage());
            System.out.println(runtime.commandInfo("main"));
        }
    }

}
