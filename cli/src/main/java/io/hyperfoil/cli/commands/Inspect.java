package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "inspect", description = "Show detailed structure of the benchmark.")
public class Inspect extends BenchmarkCommand {
   @Option(name = "pager", shortName = 'p', description = "Pager used.")
   private String pager;

   @Option(name = "max-collection-size", shortName = 'm', description = "Maximum printed size for collections and arrays.")
   private Integer maxCollectionSize;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      String structure;
      try {
         Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
         structure = benchmarkRef.structure(maxCollectionSize);
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot get benchmark " + benchmark);
      }
      invocation.context().createPager(pager).open(invocation, structure, benchmark + "-structure-", ".yaml");
      return CommandResult.SUCCESS;
   }

}
