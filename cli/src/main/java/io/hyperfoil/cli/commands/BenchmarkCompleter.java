package io.hyperfoil.cli.commands;

import java.util.stream.Stream;

public class BenchmarkCompleter extends ServerOptionCompleter {
   public BenchmarkCompleter() {
      super(client -> Stream.concat(client.benchmarks().stream(), client.templates().stream()));
   }
}
