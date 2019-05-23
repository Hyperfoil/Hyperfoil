package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "info", description = "Provides information about the benchmark.")
public class Info extends ServerCommand {
   @Argument(description = "Name of the benchmark.", completer = BenchmarkCompleter.class, required = true)
   public String benchmark;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      Client.BenchmarkRef benchmarkRef = invocation.context().client().benchmark(benchmark);
      if (benchmarkRef == null) {
         throw new CommandException("No such benchmark: '" + benchmark + "'");
      }
      invocation.context().setServerBenchmark(benchmarkRef);
      try {
         Benchmark benchmark = benchmarkRef.get();
         if (benchmark.source() == null) {
            invocation.println("No source available for benchmark '" + benchmark.name() + "'.");
         } else {
            invocation.println(benchmark.source());
         }
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Cannot get benchmark " + benchmarkRef.name());
      }
   }
}
