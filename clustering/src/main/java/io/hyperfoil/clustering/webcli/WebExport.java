package io.hyperfoil.clustering.webcli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.commands.BaseExportCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "export", description = "Export run statistics.")
public class WebExport extends BaseExportCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      Client.RunRef runRef = getRunRef(invocation);
      invocation.println("Sending exported statistics...");
      invocation.println(
            "__HYPERFOIL_DOWNLOAD_MAGIC__ /run/" + runRef.id() + "/stats/all/" + format.toLowerCase() + " " + runRef.id());
      return CommandResult.SUCCESS;
   }
}
