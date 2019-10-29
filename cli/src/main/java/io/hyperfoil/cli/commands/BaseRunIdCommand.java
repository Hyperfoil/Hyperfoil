package io.hyperfoil.cli.commands;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.aesh.command.CommandException;
import org.aesh.command.option.Argument;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;

public abstract class BaseRunIdCommand extends ServerCommand {
   @Argument(description = "ID of the run", completer = RunCompleter.class)
   private String runId;

   protected Client.RunRef getRunRef(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      Client.RunRef runRef;
      if (runId == null || runId.isEmpty()) {
         runRef = invocation.context().serverRun();
         if (runRef == null) {
            invocation.println("Command '" + getClass().getSimpleName().toLowerCase() + "' requires run ID as argument! Available runs:");
            List<Client.Run> runs = invocation.context().client().runs(false);
            printList(invocation, runs.stream().map(r -> r.id).sorted(Comparator.reverseOrder()).collect(Collectors.toList()), 15);
            throw new CommandException("Cannot run command without run ID.");
         }
      } else {
         runRef = invocation.context().client().run(runId);
         invocation.context().setServerRun(runRef);
      }
      return runRef;
   }

}
