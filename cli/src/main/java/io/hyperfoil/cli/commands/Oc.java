package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "oc", description = "Invoke Openshift client with the same parameters.", disableParsing = true)
public class Oc extends ServerCommand {

   @Arguments
   private List<String> args;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      try {
         CliUtil.execProcess(invocation, false, "oc", args.toArray(new String[0]));
      } catch (IOException e) {
         invocation.println("Failed to execute command: " + e.getLocalizedMessage());
      }
      return CommandResult.SUCCESS;
   }
}
