package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "run", description = "Starts benchmark on Hyperfoil Controller server")
public class Run extends ServerCommand {
   @Argument(description = "Name of the benchmark to be run.", completer = BenchmarkCompleter.class)
   private String benchmark;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      Client.BenchmarkRef benchmarkRef;
      if (benchmark == null) {
         benchmarkRef = invocation.context().serverBenchmark();
         if (benchmarkRef == null) {
            invocation.println("No benchmark was set. Available benchmarks: ");
            printList(invocation, invocation.context().client().benchmarks(), 15);
            return CommandResult.FAILURE;
         }
      } else {
         benchmarkRef = invocation.context().client().benchmark(benchmark);
         invocation.context().setServerBenchmark(benchmarkRef);
      }
      try {
         invocation.context().setServerRun(benchmarkRef.start());
         invocation.println("Started run " + invocation.context().serverRun().id());
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Failed to start benchmark " + benchmarkRef.name(), e);
      }
      try {
         invocation.executeCommand("status");
      } catch (Exception e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException(e);
      }
      return CommandResult.SUCCESS;
   }

   public static class BenchmarkCompleter extends ServerOptionCompleter {
      public BenchmarkCompleter() {
         super(client -> client.benchmarks().stream());
      }
   }
}
