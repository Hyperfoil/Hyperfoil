package io.hyperfoil.cli.commands;

import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

public abstract class BaseRunIdCommand extends ServerCommand {
   @Argument(description = "ID of the run", completer = RunCompleter.class)
   private String runId;

   protected Client.RunRef getRunRef(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      Client.RunRef runRef;
      if (runId == null || runId.isEmpty()) {
         runRef = invocation.context().serverRun();
         if (runRef == null) {
            failMissingRunId(invocation);
         }
      } else {
         runRef = invocation.context().client().run(runId);
         invocation.context().setServerRun(runRef);
      }
      return runRef;
   }

   protected io.hyperfoil.controller.model.Run getRun(HyperfoilCommandInvocation invocation, Client.RunRef runRef)
         throws CommandException {
      io.hyperfoil.controller.model.Run run;
      try {
         return runRef.get();
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot fetch run " + runRef.id(), e);
      }
   }
}
