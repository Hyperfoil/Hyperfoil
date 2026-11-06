package io.hyperfoil.cli.commands;

import java.nio.file.Path;
import java.util.List;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.internal.Controller;

public class LoadAndRun extends BaseStandaloneCommand {

   private static final String CMD = "run";

   public static void main(String[] args) {
      new LoadAndRun().exec(args);
   }

   @Override
   protected Class<? extends Command<HyperfoilCommandInvocation>> getCommand() {
      return LoadAndRunCommand.class;
   }

   @Override
   protected List<Class<? extends Command<HyperfoilCommandInvocation>>> getDependencyCommands() {
      return List.of(Upload.class, Wait.class, Stats.class, Report.class, Export.class);
   }

   @Override
   protected String getCommandName() {
      return CMD;
   }

   @Override
   protected boolean startLocalBeforeCommand() {
      // the controller is started by the command itself: whether it has to be clustered depends on
      // --clustered and on the benchmark, and both are known only after the command line is parsed
      return false;
   }

   @CommandDefinition(name = "run", description = "Load and start a benchmark on Hyperfoil controller server, the argument can be the benchmark definition directly.")
   public static class LoadAndRunCommand extends io.hyperfoil.cli.commands.Run {

      @Option(name = "output", shortName = 'o', description = "Output destination path for the statistics (format detected from file extension: .html, .json, or .csv/.zip; CSV output is always packaged inside a .zip archive)")
      private String output;

      @Option(name = "clustered", description = "Start the local controller in clustered mode. Inferred automatically when the benchmark declares agents.", hasValue = false)
      private boolean clustered;

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
         invocation.executeSwitchable(BaseStandaloneCommand.START_LOCAL + (clusteredRun ? " --clustered" : ""));
      }

      @Override
      protected void monitor(HyperfoilCommandInvocation invocation) throws CommandException {
         invocation.executeSwitchable("wait");
         invocation.executeSwitchable("stats -t");
         if (output != null && !output.isBlank()) {
            OutputCommandUtil.executeOutputCommand(invocation, output);
         } else {
            invocation.println("Run files are in " + runDir(invocation) + ". Use --output to generate an HTML report.");
         }
      }

      private String runDir(HyperfoilCommandInvocation invocation) {
         Client.RunRef runRef = invocation.context().serverRun();
         Path runDir = Controller.RUN_DIR;
         return (runRef == null ? runDir : runDir.resolve(runRef.id())).toString();
      }
   }
}
