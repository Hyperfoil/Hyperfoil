package io.hyperfoil.cli.commands;

import java.text.SimpleDateFormat;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "wait", description = "Wait for a specific run termination.")
public class Wait extends BaseRunIdCommand {

   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

   private boolean started = false;
   private boolean terminated = false;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      io.hyperfoil.controller.model.Run run = getRun(invocation, runRef);

      invocation.println("Monitoring run " + run.id + ", benchmark " + run.benchmark);
      if (run.description != null) {
         invocation.println(run.description);
      }

      for (;;) {
         // check if started
         if (!started && run.started != null) {
            started = true;
            invocation.println("Started:    " + DATE_FORMATTER.format(run.started));
         }

         // check if terminated
         if (!terminated && run.terminated != null) {
            terminated = true;
            invocation.println("Terminated: " + DATE_FORMATTER.format(run.terminated));
            if (!run.errors.isEmpty()) {
               invocation.println(ANSI.RED_TEXT + ANSI.BOLD + "Errors:" + ANSI.RESET);
               for (String error : run.errors) {
                  invocation.println(error);
               }
            }
            invocation.context().notifyRunCompleted(run);
            return CommandResult.SUCCESS;
         }

         try {
            run = runRef.get();
         } catch (RestClientException e) {
            if (e.getCause() instanceof InterruptedException) {
               invocation.println("");
               return CommandResult.SUCCESS;
            }
            invocation.error(e);
            throw new CommandException("Cannot monitor run " + runRef.id(), e);
         }
      }
   }
}
