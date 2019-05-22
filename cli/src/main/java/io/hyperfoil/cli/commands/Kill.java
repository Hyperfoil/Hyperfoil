package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "kill", description = "Terminate run.")
public class Kill extends BaseRunIdCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      Client.Run run = runRef.get();
      invocation.print("Kill run " + run.id + ", benchmark " + run.benchmark);
      int terminated = 0, finished = 0, running = 0;
      for (Client.Phase phase : run.phases) {
         if ("TERMINATED".equals(phase.status)) {
            terminated++;
         } else if ("FINISHED".equals(phase.status)) {
            finished++;
         } else if ("RUNNING".equals(phase.status)) {
            running++;
         }
      }
      invocation.print("(phases: " + running + " running, " + finished + " finished, " + terminated + " terminated) [y/N]: ");
      String confirmation = invocation.getShell().readLine();
      switch (confirmation.trim().toLowerCase()) {
         case "y":
         case "yes":
            break;
         default:
            invocation.println("Kill cancelled.");
            return CommandResult.SUCCESS;
      }
      try {
         runRef.kill();
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Failed to kill run " + run.id, e);
      }
      invocation.println("Killed.");
      return CommandResult.SUCCESS;
   }
}
