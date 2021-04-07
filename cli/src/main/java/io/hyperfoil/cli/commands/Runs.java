package io.hyperfoil.cli.commands;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.Table;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "runs", description = "Print info about past runs.")
public class Runs extends ServerCommand {
   private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
   private static final Table<io.hyperfoil.controller.model.Run> RUN_TABLE = new Table<io.hyperfoil.controller.model.Run>()
         .idColumns(2)
         .column("", run -> runIcon(run))
         .column("RUN_ID", run -> run.id)
         .column("BENCHMARK", run -> run.benchmark)
         .column("STARTED", run -> run.started == null ? "" : DATE_FORMATTER.format(run.started))
         .column("TERMINATED", run -> run.terminated == null ? "" : DATE_FORMATTER.format(run.terminated))
         .column("DESCRIPTION", run -> run.description);

   private static String runIcon(io.hyperfoil.controller.model.Run run) {
      if (run.cancelled) {
         return ANSI.RED_TEXT + "×" + ANSI.RESET;
      } else if (run.errors != null && !run.errors.isEmpty()) {
         return ANSI.RED_TEXT + (run.terminated == null ? ANSI.BLINK : "") + "!" + ANSI.RESET;
      } else if (run.started == null) {
         return ANSI.YELLOW_TEXT + "?" + ANSI.RESET;
      } else if (run.terminated == null) {
         return ANSI.YELLOW_TEXT + ANSI.BLINK + "?" + ANSI.RESET;
      } else {
         return ANSI.GREEN_TEXT + "+" + ANSI.RESET;
      }
   }

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      List<io.hyperfoil.controller.model.Run> runs = invocation.context().client().runs(true);
      RUN_TABLE.print(invocation, runs.stream().sorted(Comparator.comparing(run -> run.id)));
      return CommandResult.SUCCESS;
   }
}
