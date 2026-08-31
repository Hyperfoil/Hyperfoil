package io.hyperfoil.cli.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;

public class LoadAndRun extends BaseStandaloneCommand {

   private static final String CMD = "run";
   private static final String CLUSTERED = "--clustered";

   private final boolean clustered;

   public LoadAndRun(boolean clustered) {
      this.clustered = clustered;
   }

   public static void main(String[] args) {
      boolean clustered = Arrays.asList(args).contains(CLUSTERED);
      args = Stream.of(args).filter(s -> !CLUSTERED.equals(s)).toArray(String[]::new);
      LoadAndRun lr = new LoadAndRun(clustered);
      System.exit(lr.exec(args));
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
   protected String startLocalCommand() {
      if (!clustered)
         return super.startLocalCommand();

      return super.startLocalCommand() + " " + CLUSTERED;
   }

   @CommandDefinition(name = "run", description = "Load and start a benchmark on Hyperfoil controller server, the argument can be the benchmark definition directly.")
   public static class LoadAndRunCommand extends io.hyperfoil.cli.commands.Run {

      @Option(name = "output", shortName = 'o', description = "Output destination path for the HTML report")
      private String output;

      @Option(name = "print-stack-trace", hasValue = false)
      public boolean printStackTrace;

      @Option(name = "fail-on-errors", description = "Fail when the run has runtime, validation, or SLA errors", hasValue = false)
      private boolean failOnErrors;

      @Option(name = "export", description = "Destination for exported final run statistics")
      private String export;

      @Option(name = "export-format", description = "Format for --export; supported formats are JSON and CSV", defaultValue = "JSON")
      private String exportFormat;

      @Override
      protected void setup(HyperfoilCommandInvocation invocation) throws CommandException {
         parseExport();
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
         invocation.executeSwitchable("stats -t");
         if (output != null && !output.isBlank()) {
            invocation.executeSwitchable("report --silent -y --destination " + output);
         } else {
            invocation.println("Skipping report generation, consider providing --output to generate it.");
         }
         if (export != null) {
            invocation.executeSwitchable("export -y --format " + exportFormat + " --destination " + export);
         }
         if (failOnErrors) {
            failOnErrors(invocation);
         }
      }

      private void parseExport() throws CommandException {
         if (export == null) {
            return;
         }
         exportFormat = exportFormat.toUpperCase();
         if (!"JSON".equals(exportFormat) && !"CSV".equals(exportFormat)) {
            throw new CommandException("Unknown export format '" + exportFormat + "'; use JSON or CSV");
         }
         if (export.isBlank()) {
            throw new CommandException("Export destination must not be empty");
         }
      }

      private void failOnErrors(HyperfoilCommandInvocation invocation) throws CommandException {
         Client.RunRef runRef = invocation.context().serverRun();
         io.hyperfoil.controller.model.Run run = runRef.get();
         RequestStatisticsResponse stats = runRef.statsTotal();
         if (hasErrors(run, stats)) {
            throw new CommandException("Run " + runRef.id() + " completed with errors");
         }
      }

      static boolean hasErrors(io.hyperfoil.controller.model.Run run, RequestStatisticsResponse stats) {
         return run.cancelled || !run.completed || !run.errors.isEmpty() || run.phases.stream().anyMatch(phase -> phase.failed)
               || stats.statistics.stream().anyMatch(LoadAndRunCommand::hasErrors);
      }

      private static boolean hasErrors(RequestStats stats) {
         return !stats.failedSLAs.isEmpty() || stats.summary.invalid > 0 || stats.summary.requestTimeouts > 0
               || stats.summary.connectionErrors > 0 || stats.summary.internalErrors > 0;
      }
   }
}
