package io.hyperfoil.cli.commands;

import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class LoadAndRun extends BaseStandaloneCommand {

   private static final String CMD = "run";

   public static void main(String[] args) {
      LoadAndRun lr = new LoadAndRun();
      lr.exec(args);
   }

   @Override
   protected Class<? extends Command<HyperfoilCommandInvocation>> getCommand() {
      return LoadAndRunCommand.class;
   }

   @Override
   protected List<Class<? extends Command<HyperfoilCommandInvocation>>> getDependencyCommands() {
      return List.of(Upload.class, Wait.class, Report.class);
   }

   @Override
   protected String getCommandName() {
      return CMD;
   }

   @CommandDefinition(name = "run", description = "Load and start a benchmark on Hyperfoil controller server, the argument can be the benchmark definition directly.")
   public static class LoadAndRunCommand extends io.hyperfoil.cli.commands.Run {

      @Option(name = "output", shortName = 'o', description = "Output destination path for the HTML report")
      private String output;

      @Option(name = "print-stack-trace", hasValue = false)
      public boolean printStackTrace;

      @Override
      protected void setup(HyperfoilCommandInvocation invocation) throws CommandException {
         // if benchmarkFile is provided load the benchmark as first step and fail fast if something went wrong
         if (benchmark != null && !benchmark.isBlank()) {
            invocation.executeSwitchable("upload " + (printStackTrace ? "--print-stack-trace " : "") + benchmark);
            // once used, reset it as it needs to be populated by the actual benchmark name
            benchmark = null;
         } else {
            throw new CommandException("No benchmark file specified");
         }
      }

      @Override
      protected void monitor(HyperfoilCommandInvocation invocation) throws CommandException {
         invocation.executeSwitchable("wait");
         if (output != null && !output.isBlank()) {
            invocation.executeSwitchable("report --silent -y --destination " + output);
         } else {
            invocation.println("Skipping report generation, consider providing --output to generate it.");
         }
      }
   }
}
