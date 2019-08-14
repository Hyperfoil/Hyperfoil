package io.hyperfoil.cli.commands;

import java.util.Comparator;

public class RunCompleter extends ServerOptionCompleter {
   public RunCompleter() {
      super(client -> client.runs().stream().sorted(Comparator.reverseOrder()));
   }
}
