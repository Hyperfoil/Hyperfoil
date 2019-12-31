package io.hyperfoil.cli.commands;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.io.Resource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.Util;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

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
         benchmark = BenchmarkParser.instance().buildBenchmark(io.hyperfoil.core.util.Util.toString(Util.sanitize(benchmarkResource).read()), new LocalBenchmarkData());
      } catch (ParserException | BenchmarkDefinitionException e) {
         invocation.println("ERROR: " + io.hyperfoil.core.util.Util.explainCauses(e));
         throw new CommandException("Failed to parse the benchmark.", e);
      } catch (IOException e) {
         invocation.println("ERROR: " + io.hyperfoil.core.util.Util.explainCauses(e));
         throw new CommandException("Failed to load the benchmark.", e);
      }
      invocation.println("Loaded benchmark " + benchmark.name() + ", uploading...");
      try {
         ctx.setServerBenchmark(ctx.client().register(benchmark, null));
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.println("ERROR: " + io.hyperfoil.core.util.Util.explainCauses(e));
         throw new CommandException("Failed to upload the benchmark.", e);
      }
   }

}
