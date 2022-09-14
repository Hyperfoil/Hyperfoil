package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "forget", description = "Removes benchmark from controller.")
public class Forget extends BenchmarkCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      try {
         if (benchmarkRef.forget()) {
            invocation.println("Benchmark " + benchmarkRef.name() + " was deleted.");
            return CommandResult.SUCCESS;
         } else {
            invocation.error("Cannot find benchmark " + benchmarkRef.name());
            return CommandResult.FAILURE;
         }
      } catch (RestClientException e) {
         invocation.error(e);
         return CommandResult.FAILURE;
      }
   }
}
