package io.hyperfoil.cli.commands;

public class HostCompleter extends HyperfoilOptionCompleter {
   public HostCompleter() {
      super(context -> context.suggestedControllerHosts().stream());
   }
}
