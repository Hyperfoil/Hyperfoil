package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;
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
         Client.BenchmarkSource benchmarkSource = benchmarkRef.source();
         String yaml = benchmarkSource != null ? benchmarkSource.source : null;
         if (yaml != null) {
            ProvidedBenchmarkData data = new ProvidedBenchmarkData();
            data.ignoredFiles.addAll(benchmarkSource.files);
            BenchmarkSource source = BenchmarkParser.instance().createSource(yaml, data);
            List<String> missingParams = getMissingParams(source.paramsWithDefaults, currentParams);
            if (!readParams(invocation, missingParams, currentParams)) {
               return CommandResult.FAILURE;
            }
            if (source.isTemplate()) {
               boolean firstMissing = true;
               for (;;) {
                  try {
                     BenchmarkParser.instance().buildBenchmark(source, currentParams);
                     if (!data.files().isEmpty()) {
                        invocation.context().client().register(yaml, data.files(), benchmarkSource.version, benchmarkRef.name());
                     }
                     break;
                  } catch (BenchmarkData.MissingFileException e) {
                     if (firstMissing) {
                        firstMissing = false;
                        invocation.println("This benchmark template is missing some files.");
                     }
                     if (!onMissingFile(invocation, e.file, data)) {
                        return CommandResult.FAILURE;
                     }
                  }
               }
            }
         }
      } catch (RestClientException e) {
         invocation.error("Failed to retrieve source for benchmark " + benchmarkRef.name(), e);
      } catch (ParserException | BenchmarkDefinitionException e) {
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

   protected boolean onMissingFile(HyperfoilCommandInvocation invocation, String file, ProvidedBenchmarkData data) {
      try {
         Path path = CliUtil.getLocalFileForUpload(invocation, file);
         // if user does not provide uploaded file we will still run it and let the server fail
         if (path != null) {
            data.files.put(file, Files.readAllBytes(path));
         } else {
            data.ignoredFiles.add(file);
         }
         return true;
      } catch (InterruptedException interruptedException) {
         invocation.warn("Cancelled, not running anything.");
         return false;
      } catch (IOException ioException) {
         invocation.error("Cannot load " + file, ioException);
         return false;
      }
   }
}
