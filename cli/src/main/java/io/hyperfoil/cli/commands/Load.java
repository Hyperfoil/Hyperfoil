package io.hyperfoil.cli.commands;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

@CommandDefinition(name = "load", description = "Registers a benchmark definition from a local path on the Hyperfoil Controller server")
public class Load extends ServerCommand {

   @Argument(description = "YAML benchmark definition file URI", required = true)
   String benchmarkResource;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      try {
         Client.BenchmarkRef benchmarkRef = ctx.client().registerLocal(benchmarkResource, null, null);
         ctx.setServerBenchmark(benchmarkRef);
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to load the benchmark.", e);
      }
   }

}
