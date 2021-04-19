package io.hyperfoil.clustering.webcli;

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;
import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.commands.ServerCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "plot", description = "Display chart for metric/connections/sessions")
public class Plot extends ServerCommand {
   @Arguments(description = "Run plot (without args) to see detailed help.")
   private List<String> args;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      if (args == null || args.size() == 0) {
         invocation.println("Missing arguments, use " + ANSI.BOLD + "plot <type> [<name>] [<run id>]" + ANSI.RESET + "");
         invocation.println("where type is on of: metric, histogram, percentiles, connections (or conns), sessions");
         invocation.println("Examples:");
         invocation.println("    plot metric foo                Show metric 'foo' in all phases (details report)");
         invocation.println("    plot histogram my-phase foo    Show histogram of metric 'foo' in phase 'my-phase'");
         invocation.println("    plot percentiles my-phase foo  Show percentiles of metric 'foo' in phase 'my-phase'");
         invocation.println("    plot connections               Show connection pool utilization charts");
         invocation.println("    plot sessions my-phase         Show session pool charts for phase 'my-phase'");
         invocation.println("<run id> as the last argument is required only if you don't have any run in current context.");
         return CommandResult.FAILURE;
      }
      switch (args.get(0).toLowerCase()) {
         case "m":
         case "metric":
            if (args.size() < 2) {
               invocation.println("Missing name of metric. Type 'stats' to show available metrics.");
            } else {
               plotMetric(invocation, args.get(1));
            }
            break;
         case "h":
         case "histo":
         case "histogram":
            if (args.size() < 3) {
               invocation.println("Missing name of phase and metric. Type 'stats' to show available metrics.");
            } else {
               plotHistogram(invocation, args.get(1), args.get(2));
            }
            break;
         case "p":
         case "percentiles":
            if (args.size() < 3) {
               invocation.println("Missing name of phase and metric. Type 'stats' to show available metrics.");
            } else {
               plotPercentiles(invocation, args.get(1), args.get(2));
            }
            break;
         case "c":
         case "conns":
         case "connections":
            plotConnections(invocation);
            break;
         case "s":
         case "sessions":
            if (args.size() < 2) {
               plotSessions(invocation, null);
            } else {
               plotSessions(invocation, args.get(1));
            }
      }
      return CommandResult.SUCCESS;
   }

   private void plotMetric(HyperfoilCommandInvocation invocation, String metric) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation, 2);
      plotIframe(invocation, runRef, "/details/" + metric);
   }

   private void plotHistogram(HyperfoilCommandInvocation invocation, String phase, String metric) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation, 3);
      plotIframe(invocation, runRef, "/histogram/" + phase + "/" + metric);
   }

   private void plotPercentiles(HyperfoilCommandInvocation invocation, String phase, String metric) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation, 3);
      plotIframe(invocation, runRef, "/percentiles/" + phase + "/" + metric);
   }

   private void plotConnections(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation, 1);
      plotIframe(invocation, runRef, "/connections");
   }

   private void plotSessions(HyperfoilCommandInvocation invocation, String phase) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation, phase == null ? 1 : 2);
      plotIframe(invocation, runRef, "/sessions" + (phase == null ? "" : "/" + phase));
   }

   private Client.RunRef getRunRef(HyperfoilCommandInvocation invocation, int runArgIndex) throws CommandException {
      Client.RunRef runRef;
      if (args.size() > runArgIndex && !args.get(runArgIndex).isEmpty()) {
         runRef = invocation.context().client().run(args.get(runArgIndex));
      } else {
         runRef = invocation.context().serverRun();
         if (runRef == null) {
            failMissingRunId(invocation);
         }
      }
      return runRef;
   }

   private void plotIframe(HyperfoilCommandInvocation invocation, Client.RunRef runRef, String path) {
      invocation.println("__HYPERFOIL_RAW_HTML_START__" +
            "<iframe onload=\"resizeFrame(this)\" class=\"plot\" src=\"/run/" + runRef.id() + "/report?unwrap=true#" + path + "\"></iframe>" +
            "<button class=\"plottoggle hfbutton\" onclick=\"togglePlot(this)\">Collapse</button>" +
            "__HYPERFOIL_RAW_HTML_END__");
   }
}
