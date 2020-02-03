package io.hyperfoil.cli.commands;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "exit", description = "exit the program", aliases = { "quit" })
public class Exit implements Command<HyperfoilCommandInvocation> {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) {
      invocation.stop();
      return CommandResult.SUCCESS;
   }
}
