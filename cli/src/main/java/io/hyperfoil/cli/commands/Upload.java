package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.util.ArrayList;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.io.Resource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class Upload extends ServerCommand {

   @Argument(description = "YAML benchmark definition file", required = true)
   Resource benchmarkResource;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      Benchmark benchmark;
      try {
         benchmark = BenchmarkParser.instance().buildBenchmark(Util.toString(io.hyperfoil.cli.Util.sanitize(benchmarkResource).read()), new LocalBenchmarkData());
      } catch (ParserException | BenchmarkDefinitionException e) {
         invocation.error(e);
         throw new CommandException("Failed to parse the benchmark.", e);
      } catch (IOException e) {
         invocation.error(e);
         throw new CommandException("Failed to load the benchmark.", e);
      }
      invocation.println("Loaded benchmark " + benchmark.name() + ", uploading...");
      // Note: we are loading and serializing the benchmark here just to fail fast - actual upload
      // will be done in text+binary form to avoid the pain with syncing client and server
      try {
         io.hyperfoil.util.Util.serialize(benchmark);
      } catch (IOException e) {
         invocation.error("Failed to serialize the benchmark: " + Util.explainCauses(e));
      }
      try {
         Client.BenchmarkRef benchmarkRef = ctx.client().register(
               benchmarkResource.getAbsolutePath(), new ArrayList<>(benchmark.files().keySet()), null, null);
         ctx.setServerBenchmark(benchmarkRef);
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to upload the benchmark.", e);
      }
   }

}
