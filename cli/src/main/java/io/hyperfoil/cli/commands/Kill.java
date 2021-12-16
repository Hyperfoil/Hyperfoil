package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.model.Phase;

@CommandDefinition(name = "kill", description = "Terminate run.")
public class Kill extends BaseRunIdCommand {
   @Option(shortName = 'y', description = "Assume yes for all interactive questions.", hasValue = false)
   public boolean assumeYes;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      io.hyperfoil.controller.model.Run run = runRef.get();
      if (!assumeYes) {
         invocation.print("Kill run " + run.id + ", benchmark " + run.benchmark);
         int terminated = 0, finished = 0, running = 0;
         for (Phase phase : run.phases) {
            if ("TERMINATED".equals(phase.status)) {
               terminated++;
            } else if ("FINISHED".equals(phase.status)) {
               finished++;
            } else if ("RUNNING".equals(phase.status)) {
               running++;
            }
         }
         invocation.print("(phases: " + running + " running, " + finished + " finished, " + terminated + " terminated) [y/N]: ");
         if (!readYes(invocation)) {
            invocation.println("Kill cancelled.");
            return CommandResult.SUCCESS;
         }
      }
      try {
         runRef.kill();
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to kill run " + run.id, e);
      }
      invocation.println("Killed.");
      return CommandResult.SUCCESS;
   }
}
