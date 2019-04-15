package io.hyperfoil.cli.commands;

import java.io.IOException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.io.Resource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
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
         benchmark = BenchmarkParser.instance().buildBenchmark(Util.toString(benchmarkResource.read()), new LocalBenchmarkData());
      } catch (ParserException e) {
         throw new CommandException("Failed to parse the benchmark.", e);
      } catch (IOException e) {
         throw new CommandException("Failed to load the benchmark.", e);
      }
      invocation.println("Loaded benchmark " + benchmark.name() + ", uploading...");
      try {
         ctx.setServerBenchmark(ctx.client().register(benchmark));
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (Exception e) {
         throw new CommandException("Failed to upload the benchmark.", e);
      }
   }

}
