package io.hyperfoil.cli.commands;

public class BenchmarkCompleter extends ServerOptionCompleter {
   public BenchmarkCompleter() {
      super(client -> client.benchmarks().stream());
   }
}
