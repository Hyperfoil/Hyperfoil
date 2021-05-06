package io.hyperfoil.cli.commands;

import java.text.SimpleDateFormat;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.model.Phase;

@CommandDefinition(name = "status", description = "Prints information about executing or completed run.")
public class Status extends BaseRunIdCommand {
   private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss.SSS");
   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
   private static final Table<Phase> PHASE_TABLE = new Table<Phase>()
         .column("NAME", p -> p.name)
         .column("STATUS", p -> p.status)
         .column("STARTED", p -> p.started == null ? null : TIME_FORMATTER.format(p.started))
         .column("REMAINING", p -> p.remaining, Table.Align.RIGHT)
         .column("COMPLETED", p -> p.completed == null ? null : TIME_FORMATTER.format(p.completed))
         .column("TOTAL DURATION", p -> p.totalDuration)
         .column("DESCRIPTION", p -> p.description);

   @Option(name = "all", shortName = 'a', description = "Show all phases", hasValue = false)
   boolean all;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      io.hyperfoil.controller.model.Run run;
      try {
         run = runRef.get();
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot fetch status for run " + runRef.id(), e);
      }
      invocation.println("Run " + run.id + ", benchmark " + run.benchmark);
      if (run.description != null) {
         invocation.println(run.description);
      }
      for (; ; ) {
         int lines = 0;
         if (run.agents != null && !run.agents.isEmpty()) {
            invocation.print("Agents: ");
            invocation.println(String.join(", ", run.agents.stream().map(a -> a.name + "[" + a.status + "]").toArray(String[]::new)));
            ++lines;
         }
         if (run.started != null) {
            invocation.print("Started: " + DATE_FORMATTER.format(run.started) + "    ");
         }
         if (run.terminated != null) {
            invocation.println("Terminated: " + DATE_FORMATTER.format(run.terminated));
         } else {
            invocation.println("");
         }
         ++lines;

         io.hyperfoil.controller.model.Run r = run;
         lines += PHASE_TABLE.print(invocation, run.phases.stream().filter(p -> showPhase(r, p)));
         long cancelled = run.phases.stream().filter(p -> "CANCELLED".equals(p.status)).count();
         if (cancelled > 0) {
            invocation.println(cancelled + " phases were cancelled.");
            lines++;
         }
         if (!run.errors.isEmpty()) {
            invocation.println("Errors:");
            for (String note : run.errors) {
               invocation.println(note);
            }
            lines += 1 + run.errors.size();
         }
         if (run.terminated != null) {
            invocation.context().notifyRunCompleted(run);
            return CommandResult.SUCCESS;
         }
         if (interruptibleDelay(invocation)) {
            return CommandResult.SUCCESS;
         }
         ++lines;
         try {
            run = runRef.get();
         } catch (RestClientException e) {
            if (e.getCause() instanceof InterruptedException) {
               clearLines(invocation, 1);
               invocation.println("");
               return CommandResult.SUCCESS;
            }
            invocation.error(e);
            throw new CommandException("Cannot fetch status for run " + runRef.id(), e);
         }

         clearLines(invocation, lines);
      }
   }

   private boolean showPhase(io.hyperfoil.controller.model.Run run, Phase phase) {
      return ((all || run.terminated != null) && !"CANCELLED".equals(phase.status))
            || "RUNNING".equals(phase.status) || "FINISHED".equals(phase.status);
   }

}
