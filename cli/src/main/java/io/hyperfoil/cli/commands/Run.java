package io.hyperfoil.cli.commands;

import java.util.Collection;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.cli.context.HyperfoilCompleterData;
import io.hyperfoil.client.Client;

@CommandDefinition(name = "run", description = "Starts benchmark on Hyperfoil Controller server")
public class Run extends ServerCommand {
   // TODO: add completer
   @Argument(description = "Name of the benchmark to be run.", completer = BenchmarkCompleter.class)
   private String benchmark;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      if (!ensureConnection(invocation)) {
         return CommandResult.FAILURE;
      }
      Client.BenchmarkRef benchmarkRef;
      if (benchmark == null) {
         benchmarkRef = invocation.context().serverBenchmark();
         if (benchmarkRef == null) {
            invocation.println("No benchmark was set. Available benchmarks: ");
            int counter = 0;
            Collection<String> benchmarks = invocation.context().client().benchmarks();
            for (String name : benchmarks) {
               invocation.println("* " + name);
               if (counter++ > 15) {
                  invocation.println("... (" + (benchmarks.size() - 15) + " more) ...");
                  break;
               }
            }
            return CommandResult.FAILURE;
         }
      } else {
         benchmarkRef = invocation.context().client().benchmark(benchmark);
         invocation.context().setServerBenchmark(benchmarkRef);
      }
      try {
         invocation.context().setServerRun(benchmarkRef.start());
         invocation.println("Started run " + invocation.context().serverRun().id());
         return CommandResult.SUCCESS;
      } catch (Exception e) {
         invocation.println("Failed to start benchmark " + benchmarkRef.name());
         e.printStackTrace();
         return CommandResult.FAILURE;
      }
   }

   public static class BenchmarkCompleter implements OptionCompleter<HyperfoilCompleterData> {
      @Override
      public void complete(HyperfoilCompleterData completerInvocation) {
         HyperfoilCliContext context = completerInvocation.getContext();
         if (context.client() == null) {
            return;
         }
         Stream<String> benchmarks;
         try {
            benchmarks = context.client().benchmarks().stream();
         } catch (Exception e) {
            return;
         }
         String prefix = completerInvocation.getGivenCompleteValue();
         if (prefix != null) {
            benchmarks = benchmarks.filter(b -> b.startsWith(prefix));
         }
         benchmarks.forEach(completerInvocation::addCompleterValue);
      }
   }
}
