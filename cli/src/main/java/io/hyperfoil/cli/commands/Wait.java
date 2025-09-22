package io.hyperfoil.cli.commands;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.RequestStatisticsResponse;

@CommandDefinition(name = "wait", description = "Wait for a specific run termination.")
public class Wait extends BaseRunIdCommand {

   private boolean started = false;
   private boolean terminated = false;
   private boolean persisted = false;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      io.hyperfoil.controller.model.Run run = getRun(invocation, runRef);

      invocation.println("Monitoring run " + runRef.id() + ", benchmark " + runRef.benchmark().name());
      if (run.description != null) {
         invocation.println(run.description);
      }

      for (;;) {
         RequestStatisticsResponse recent = runRef.statsRecent();

         // check if started
         if (!started && "RUNNING".equals(recent.status)) {
            started = true;
            run = runRef.get();
            invocation.println("Started:    " + DATE_FORMATTER.format(run.started));
         }

         // check if terminated
         if (!terminated && "TERMINATED".equals(recent.status)) {
            terminated = true;
            run = runRef.get();
            invocation.println("Terminated: " + DATE_FORMATTER.format(run.terminated));
            if (!run.errors.isEmpty()) {
               invocation.println(ANSI.RED_TEXT + ANSI.BOLD + "Errors:" + ANSI.RESET);
               for (String error : run.errors) {
                  invocation.println(error);
               }
            }
            invocation.context().notifyRunCompleted(run);
         }

         // right now if for some reason the run.persisted is not set to true, the process will wait forever.
         // TODO: we could implement a timeout to be safe
         if (terminated && !persisted) {
            run = runRef.get();
            // this monitoring will guarantee that if we try to run the report, the all.json is already persisted
            if (run.persisted) {
               persisted = true;
               invocation.println("Run statistics persisted in the local filesystem");
               return CommandResult.SUCCESS;
            }
         }

         try {
            Thread.sleep(1000);
         } catch (InterruptedException e) {
            invocation.error(e);
            throw new CommandException("Cannot monitor run " + runRef.id(), e);
         }
      }
   }
}
