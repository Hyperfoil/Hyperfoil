package io.hyperfoil.cli.commands;

import java.text.SimpleDateFormat;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;

@CommandDefinition(name = "status", description = "Prints information about executing or completed run.")
public class Status extends BaseRunIdCommand {
   private static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss.S");
   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
   private static final Table<Client.Phase> PHASE_TABLE = new Table<Client.Phase>()
         .column("NAME", p -> p.name)
         .column("STATUS", p -> p.status)
         .column("STARTED", p -> p.started == null ? null : TIME_FORMATTER.format(p.started))
         .column("REMAINING", p -> p.remaining)
         .column("FINISHED", p -> p.finished == null ? null : TIME_FORMATTER.format(p.finished))
         .column("TOTAL DURATION", p -> p.totalDuration);

   @Option(name = "all", shortName = 'a', description = "Show all phases", hasValue = false)
   boolean all;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      Client.RunRef runRef = getRunRef(invocation);
      Client.Run run;
      try {
         run = runRef.get();
      } catch (Exception e) {
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
         if (run.terminated != null) {
            return CommandResult.SUCCESS;
         }
         invocation.println("Press Ctrl+C to stop watching...");
         Thread.sleep(1000);
         try {
            run = runRef.get();
         } catch (Exception e) {
            throw new CommandException("Cannot fetch status for run " + runRef.id(), e);
         }
         clearLines(invocation, 3 + (int) r.phases.stream().filter(p -> showPhase(r, p)).count());
      }
   }

   private boolean showPhase(Client.Run run, Client.Phase phase) {
      return all || run.terminated != null || "RUNNING".equals(phase.status) || "FINISHED".equals(phase.status);
   }

}
