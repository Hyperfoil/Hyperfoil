package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;

@CommandDefinition(name = "version", description = "Provides server/client information.")
public class Version extends ServerCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      try {
         io.hyperfoil.controller.model.Version serverVersion = invocation.context().client().version();
         invocation.println("Server: " + serverVersion.version + ", " + serverVersion.commitId);
      } catch (RestClientException e) {
         invocation.println("Server: unknown");
      }
      invocation.println("Client: " + io.hyperfoil.api.Version.VERSION + ", " + io.hyperfoil.api.Version.COMMIT_ID);
      return CommandResult.SUCCESS;
   }
}
