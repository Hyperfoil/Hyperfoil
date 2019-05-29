package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "info", description = "Provides information about the benchmark.")
public class Info extends BenchmarkCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
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
