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
import io.hyperfoil.api.statistics.LongValue;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import org.aesh.command.AeshCommandRuntimeBuilder;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.CommandRuntime;
import org.aesh.command.impl.registry.AeshCommandRegistryBuilder;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@CommandDefinition(name = "read-yaml", description = "read-yaml command to initiate a hyperfoil workload through a yaml file")
public class ReadYaml implements Command<CommandInvocation> {

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
    public CommandResult execute(CommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if(help) {
            commandInvocation.println(commandInvocation.getHelpInfo("main"));
            return CommandResult.SUCCESS;
        }

        Benchmark benchmark = null;
        try {
            benchmark = buildBenchmark(yaml.read(), commandInvocation);

            if(benchmark != null) {
                LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
                commandInvocation.println("Running for " + benchmark.simulation().statisticsCollectionPeriod());
                commandInvocation.println(benchmark.simulation().threads() + " threads");
                runner.run();
                StatisticsCollector collector = new StatisticsCollector(benchmark.simulation());
                runner.visitStatistics(collector);
                collector.visitStatistics((phase, sequence, stats) -> {
                    printStats(stats, commandInvocation);
                });
            }
        }
        catch(FileNotFoundException e){
            commandInvocation.println("Couldn't find yaml file: "+e.getMessage());
        }
        return CommandResult.SUCCESS;
    }

    private Benchmark buildBenchmark(InputStream inputStream, CommandInvocation invocation){
        if (inputStream == null)
            invocation.println("Could not find benchmark configuration");

        try {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String source = result.toString(StandardCharsets.UTF_8.name());
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source);

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

    private void printStats(StatisticsSnapshot stats, CommandInvocation invocation) {
        long dataRead = ((LongValue) stats.custom.get("bytes")).value();
        double durationSeconds = (stats.histogram.getEndTimeStamp() - stats.histogram.getStartTimeStamp()) / 1000d;
        invocation.println(stats.histogram.getTotalCount()+" requests in "+durationSeconds+"s, "+formatData(dataRead)+" read");
        invocation.println("                 Avg    Stdev      Max");
        invocation.println("Latency:      "+formatTime(stats.histogram.getMean())+" "+formatTime(stats.histogram.getStdDeviation())+" "+formatTime(stats.histogram.getMaxValue()));
         /*
         if (latency) {
            invocation.println("Latency Distribution");
            for (double percentile : new double[] { 0.5, 0.75, 0.9, 0.99, 0.999, 0.9999, 0.99999, 1.0}) {
               invocation.println(String.format("%7.3f", 100 * percentile)+" "+formatTime(stats.histogram.getValueAtPercentile(100 * percentile)));
            }
            invocation.println("----------------------------------------------------------");
            invocation.println("Detailed Percentile Spectrum");
            invocation.println("   Value  Percentile  TotalCount  1/(1-Percentile)");
            for (HistogramIterationValue value : stats.histogram.percentiles(5)) {
               invocation.println(formatTime(value.getValueIteratedTo())+" "+String.format("%9.5f%%  %10d  %15.2f",
                     value.getPercentile(), value.getTotalCountToThisValue(), 100/(100 - value.getPercentile())));
            }
            invocation.println("----------------------------------------------------------");
         }
         */
        invocation.println("Requests/sec: "+stats.histogram.getTotalCount() / durationSeconds);
        if (stats.errors() > 0) {
            invocation.println("Socket errors: connect "+stats.connectFailureCount+", reset "+stats.resetCount+", timeout "+stats.timeouts);
            invocation.println("Non-2xx or 3xx responses: "+ stats.status_4xx + stats.status_5xx + stats.status_other);
        }
        invocation.println("Transfer/sec: "+formatData(dataRead / durationSeconds));
    }

    private String formatData(double value) {
        double scaled;
        String suffix;
        if (value >= 1024 * 1024 * 1024) {
            scaled = (double) value / (1024 * 1024 * 1024);
            suffix = "GB";
        } else if (value >= 1024 * 1024) {
            scaled = (double) value / (1024 * 1024);
            suffix = "MB";
        }  else if (value >= 1024) {
            scaled = (double) value / 1024;
            suffix = "kB";
        } else {
            scaled = value;
            suffix = "B ";
        }
        return String.format("%6.2f%s", scaled, suffix);
    }

    private String formatTime(double value) {
        String suffix = "ns";
        if (value >= 1000_000_000) {
            value /= 1000_000_000;
            suffix = "s ";
        } else if (value >= 1000_000) {
            value /= 1000_000;
            suffix = "ms";
        } else if (value >= 1000) {
            value /= 1000;
            suffix = "us";
        }
        return String.format("%6.2f%s", value, suffix);
    }

    public static void main(String[] args) throws Exception {
        CommandRuntime runtime =
                AeshCommandRuntimeBuilder.builder()
                                         .commandRegistry(AeshCommandRegistryBuilder.builder()
                                                                  .command(ReadYaml.class).create())
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
