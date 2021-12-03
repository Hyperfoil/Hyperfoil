package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.io.Resource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class Upload extends ServerCommand {

   @Argument(description = "YAML benchmark definition file", required = true)
   Resource benchmarkResource;

   @Option(name = "print-stack-trace", hasValue = false)
   boolean printStackTrace;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      Resource sanitizedResource = CliUtil.sanitize(benchmarkResource);
      Benchmark benchmark;
      try {
         benchmark = BenchmarkParser.instance().buildBenchmark(Util.toString(sanitizedResource.read()), new LocalBenchmarkData(Paths.get(sanitizedResource.getAbsolutePath())));
      } catch (ParserException | BenchmarkDefinitionException e) {
         logError(invocation, e);
         throw new CommandException("Failed to parse the benchmark.", e);
      } catch (IOException e) {
         logError(invocation, e);
         throw new CommandException("Failed to load the benchmark.", e);
      } catch (Exception e) {
         logError(invocation, e);
         throw new CommandException("Unknown error.", e);
      }
      invocation.println("Loaded benchmark " + benchmark.name() + ", uploading...");
      // Note: we are loading and serializing the benchmark here just to fail fast - actual upload
      // will be done in text+binary form to avoid the pain with syncing client and server
      try {
         Util.serialize(benchmark);
      } catch (IOException e) {
         invocation.error("Failed to serialize the benchmark: " + Util.explainCauses(e));
      }
      try {
         Path benchmarkDir = Paths.get(sanitizedResource.getAbsolutePath()).getParent();
         Map<String, Path> extraFiles = benchmark.files().keySet().stream()
               .collect(Collectors.toMap(file -> file, file -> {
                  Path path = Paths.get(file);
                  return path.isAbsolute() ? path : benchmarkDir.resolve(file);
               }));
         Client.BenchmarkRef benchmarkRef = ctx.client().register(
               sanitizedResource.getAbsolutePath(), extraFiles, null, null);
         ctx.setServerBenchmark(benchmarkRef);
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to upload the benchmark.", e);
      }
   }

   private void logError(HyperfoilCommandInvocation invocation, Exception e) {
      invocation.error(e);
      if (printStackTrace) {
         invocation.printStackTrace(e);
      } else {
         invocation.println("Use --print-stack-trace to display the whole stack trace of this error.");
      }
   }

}
