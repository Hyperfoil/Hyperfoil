package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "report", description = "Generate HTML report")
public class Report extends BaseRunIdCommand {
   @Option(shortName = 's', description = "Other file (in given run) to use as report input.")
   protected String source;

   @Option(shortName = 'd', description = "Destination path to the HTML report", required = true, askIfNotSet = true)
   private String destination;

   @Option(shortName = 'y', description = "Assume yes for all interactive questions.", hasValue = false)
   public boolean assumeYes;

   @Option(description = "Do not open the HTML report automatically", hasValue = false)
   public boolean silent;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.RunRef runRef = getRunRef(invocation);
      File destination = new File(this.destination);
      if (destination.exists()) {
         if (destination.isFile()) {
            if (!askForOverwrite(invocation, destination)) {
               invocation.println("Cancelled. You can change destination file with '-d /path/to/report.html'");
               return CommandResult.SUCCESS;
            }
         } else if (destination.isDirectory()) {
            destination = destination.toPath().resolve(runRef.id() + ".html").toFile();
            if (destination.exists()) {
               if (destination.isFile()) {
                  if (!askForOverwrite(invocation, destination)) {
                     invocation.println("Cancelled. You can change destination file with '-d /path/to/report.html'");
                     return CommandResult.SUCCESS;
                  }
               } else if (destination.isDirectory()) {
                  invocation.println("Both " + this.destination + " and " + destination + " are directories. Please use another path.");
                  return CommandResult.SUCCESS;
               }
            }
         }
      }
      try {
         byte[] report = runRef.report(source);
         Files.write(destination.toPath(), report);
      } catch (RestClientException e) {
         invocation.error("Cannot fetch report for run " + runRef.id(), e);
         return CommandResult.FAILURE;
      } catch (IOException e) {
         invocation.error("Cannot write to '" + destination + "': ", e);
         return CommandResult.FAILURE;
      }
      invocation.println("Written to " + destination);
      if (!"true".equalsIgnoreCase(System.getenv("HYPERFOIL_CONTAINER")) && !silent) {
         openInBrowser("file://" + destination);
      }
      return CommandResult.SUCCESS;
   }

   private boolean askForOverwrite(HyperfoilCommandInvocation invocation, File destination) {
      if (assumeYes) {
         return true;
      }
      invocation.print("File " + destination + " already exists, overwrite? [y/N]: ");
      boolean overwrite = false;
      try {
         if (readYes(invocation)) {
            overwrite = true;
         }
      } catch (InterruptedException e) {
         // ignore
      }
      return overwrite;
   }

}
