package io.hyperfoil.cli.commands;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "inspect", description = "Show detailed structure of the benchmark.")
public class Inspect extends ParamsCommand {
   @Option(name = "pager", shortName = 'p', description = "Pager used.")
   private String pager;

   @Option(name = "max-collection-size", shortName = 'm', description = "Maximum printed size for collections and arrays.")
   private Integer maxCollectionSize;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      Client.BenchmarkRef benchmarkRef;
      Client.BenchmarkStructure structure;
      try {
         benchmarkRef = ensureBenchmark(invocation);
         structure = benchmarkRef.structure(maxCollectionSize, Collections.emptyMap());
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot get benchmark " + benchmark);
      }
      if (structure.params != null && !structure.params.isEmpty()) {
         invocation.println("Benchmark template '" + benchmarkRef.name() + "' has these parameters and default values:\n");
         printTemplateParams(invocation, structure.params);
         invocation.print("Do you want to display structure with a resolved template? [y/N]: ");
         if (readYes(invocation)) {
            Map<String, String> currentParams = getParams(invocation);
            List<String> missingParams = getMissingParams(structure.params, currentParams);
            if (!readParams(invocation, missingParams, currentParams)) {
               return CommandResult.FAILURE;
            }
            try {
               structure = benchmarkRef.structure(maxCollectionSize, currentParams);
            } catch (RestClientException e) {
               invocation.error(e);
               throw new CommandException("Cannot get benchmark " + benchmark);
            }
            invocation.context().setCurrentParams(currentParams);
         }
      }
      if (structure.content != null) {
         invocation.context().createPager(pager).open(invocation, structure.content, benchmark + "-structure-", ".yaml");
      }
      return CommandResult.SUCCESS;
   }
}
