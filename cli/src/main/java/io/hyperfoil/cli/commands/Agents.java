package io.hyperfoil.cli.commands;

import java.util.Collection;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;

@CommandDefinition(name = "agents", description = "List agents registered on the controller.")
public class Agents extends ServerCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      try {
         Collection<Client.Agent> agents = invocation.context().client().agents();
         for (Client.Agent agent : agents) {
            invocation.println("* " + agent.name + "[" + agent.status + "]");
         }
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         throw new CommandException("Failed to list agents", e);
      }
   }
}
