package io.hyperfoil.cli.commands;

import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;

public abstract class BenchmarkCommand extends ServerCommand {
   @Argument(description = "Name of the benchmark.", completer = BenchmarkCompleter.class)
   public String benchmark;

   protected Client.BenchmarkRef ensureBenchmark(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      Client.BenchmarkRef benchmarkRef;
      if (benchmark == null || benchmark.isEmpty()) {
         benchmarkRef = ctx.serverBenchmark();
         if (benchmarkRef == null) {
            invocation.println("No benchmark was set. Available benchmarks: ");
            printList(invocation, invocation.context().client().benchmarks(), 15);
            throw new CommandException("Use " + getClass().getSimpleName().toLowerCase() + " <benchmark>");
         }
      } else {
         benchmarkRef = ctx.client().benchmark(benchmark);
         if (benchmarkRef == null) {
            invocation.println("Available benchmarks: ");
            printList(invocation, invocation.context().client().benchmarks(), 15);
            throw new CommandException("No such benchmark: '" + benchmark + "'");
         }
      }
      ctx.setServerBenchmark(benchmarkRef);
      return benchmarkRef;
   }
}
