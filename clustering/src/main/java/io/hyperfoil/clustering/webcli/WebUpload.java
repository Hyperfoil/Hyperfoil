package io.hyperfoil.clustering.webcli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class WebUpload implements Command<HyperfoilCommandInvocation> {
   @Argument(description = "Argument ignored (provided only for compatibility).")
   String dummy;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) {
      invocation.println("__HYPERFOIL_UPLOAD_MAGIC__");
      return CommandResult.SUCCESS;
   }
}
