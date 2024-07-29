package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "info", description = "Provides information about the benchmark.")
public class Info extends BenchmarkCommand {
   @Option(name = "pager", shortName = 'p', description = "Pager used.")
   private String pager;

   @Option(name = "run", shortName = 'r', description = "Show benchmark used for specific run.", completer = RunCompleter.class)
   private String runId;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      String benchmarkName = "<unknown>";
      Client.BenchmarkSource source;
      try {
         if (runId != null) {
            Client.RunRef run = invocation.context().client().run(runId);
            benchmarkName = run.get().benchmark;
            source = invocation.context().client().benchmark(benchmarkName).source();
         } else {
            Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
            benchmarkName = benchmarkRef.name();
            source = benchmarkRef.source();
         }
         if (source == null) {
            invocation.println("No source available for benchmark '" + benchmarkName + "'.");
         } else {
            invocation.context().createPager(pager).open(invocation, source.source, benchmarkName + "-", ".yaml");
         }
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot get benchmark " + benchmarkName);
      }
   }
}
