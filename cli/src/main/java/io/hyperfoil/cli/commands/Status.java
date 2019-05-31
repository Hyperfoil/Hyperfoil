package io.hyperfoil.cli.commands;

import java.text.SimpleDateFormat;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "status", description = "Prints information about executing or completed run.")
public class Status extends BaseRunIdCommand {
   private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss.S");
   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
   private static final Table<Client.Phase> PHASE_TABLE = new Table<Client.Phase>()
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
      Client.Run run;
      try {
         run = runRef.get();
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Cannot fetch status for run " + runRef.id(), e);
      }
      invocation.println("Run " + run.id + ", benchmark " + run.benchmark);
      if (run.description != null) {
         invocation.println(run.description);
      }
      if (run.agents != null && !run.agents.isEmpty()) {
         invocation.print("Agents: ");
         invocation.println(String.join(", ", run.agents.stream().map(a -> a.name + "[" + a.status + "]").toArray(String[]::new)));
      }
      for (;;) {
         if (run.started != null) {
            invocation.print("Started: " + DATE_FORMATTER.format(run.started) + "    ");
         }
         if (run.terminated != null) {
            invocation.println("Terminated: " + DATE_FORMATTER.format(run.terminated));
         } else {
            invocation.println("");
         }

         Client.Run r = run;
         invocation.print(PHASE_TABLE.print(run.phases.stream().filter(p -> showPhase(r, p))));
         long cancelled = run.phases.stream().filter(p -> "CANCELLED".equals(p.status)).count();
         if (cancelled > 0) {
            invocation.println(cancelled + " phases were cancelled.");
         }
         if (!run.notes.isEmpty()) {
            invocation.println("Notes:");
            for (String note : run.notes) {
               invocation.println(note);
            }
         }
         if (run.terminated != null) {
            return CommandResult.SUCCESS;
         }
         if (interruptibleDelay(invocation)) {
            return CommandResult.SUCCESS;
         }
         try {
            run = runRef.get();
         } catch (RestClientException e) {
            invocation.println("ERROR: " + Util.explainCauses(e));
            throw new CommandException("Cannot fetch status for run " + runRef.id(), e);
         }
         int lines = 3;
         lines += (int) r.phases.stream().filter(p -> showPhase(r, p)).count();
         lines += cancelled > 0 ? 1 : 0;
         lines += run.notes.isEmpty() ? 0 : run.notes.size() + 1;
         clearLines(invocation, lines);
      }
   }

   private boolean showPhase(Client.Run run, Client.Phase phase) {
      return ((all || run.terminated != null) && !"CANCELLED".equals(phase.status))
            || "RUNNING".equals(phase.status) || "FINISHED".equals(phase.status);
   }

}
