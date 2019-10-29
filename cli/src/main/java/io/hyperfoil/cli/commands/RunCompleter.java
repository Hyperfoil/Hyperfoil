package io.hyperfoil.cli.commands;

import java.util.Comparator;

public class RunCompleter extends ServerOptionCompleter {
   public RunCompleter() {
      super(client -> client.runs(false).stream().map(r -> r.id).sorted(Comparator.reverseOrder()));
   }
}
