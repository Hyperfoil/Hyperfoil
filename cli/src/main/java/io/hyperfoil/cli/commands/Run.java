package io.hyperfoil.cli.commands;

import java.util.List;
import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

@CommandDefinition(name = "run", description = "Starts benchmark on Hyperfoil Controller server")
public class Run extends ParamsCommand {
   @Option(shortName = 'd', description = "Run description")
   String description;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      Map<String, String> currentParams = getParams(invocation);

      try {
         String yaml = benchmarkRef.source().source;
         if (yaml != null) {
            BenchmarkSource source = BenchmarkParser.instance().createSource(yaml, BenchmarkData.EMPTY);
            List<String> missingParams = getMissingParams(source.paramsWithDefaults, currentParams);
            if (!readParams(invocation, missingParams, currentParams)) {
               return CommandResult.FAILURE;
            }
         }
      } catch (RestClientException e) {
         invocation.error("Failed to retrieve source for benchmark " + benchmarkRef.name(), e);
      } catch (ParserException e) {
         invocation.error("Failed to parse retrieved source for benchmark " + benchmarkRef.name(), e);
         return CommandResult.FAILURE;
      }
      invocation.context().setCurrentParams(currentParams);

      try {
         invocation.context().setServerRun(benchmarkRef.start(description, currentParams));
         invocation.println("Started run " + invocation.context().serverRun().id());
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to start benchmark " + benchmarkRef.name(), e);
      }
      try {
         invocation.executeCommand("status");
      } catch (Exception e) {
         invocation.error(e);
         throw new CommandException(e);
      }
      return CommandResult.SUCCESS;
   }

}
