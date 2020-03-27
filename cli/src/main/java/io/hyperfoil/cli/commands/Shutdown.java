package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "shutdown", description = "Terminate the controller")
public class Shutdown extends ServerCommand {
   @Option(description = "If true, terminate even if a run is in progress.")
   public boolean force;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      invocation.context().client().shutdown(force);
      return CommandResult.SUCCESS;
   }
}
