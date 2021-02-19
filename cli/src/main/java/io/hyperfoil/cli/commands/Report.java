package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "report", description = "Generate HTML report")
public class Report extends BaseReportCommand {

   @Option(shortName = 'd', description = "Destination path to the HTML report", required = true, askIfNotSet = true)
   private String destination;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      String report = getReport(invocation);

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
         Files.write(destination.toPath(), report.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot write to '" + destination.toString() + "': ", e);
      }
      invocation.println("Written to " + destination);
      if (!"true".equalsIgnoreCase(System.getenv("HYPERFOIL_CONTAINER"))) {
         openInBrowser("file://" + destination.toString());
      }
      return CommandResult.SUCCESS;
   }

   private boolean askForOverwrite(HyperfoilCommandInvocation invocation, File destination) {
      invocation.print("File " + destination + " already exists, overwrite? [y/N]: ");
      boolean overwrite = false;
      try {
         String confirmation = invocation.getShell().readLine();
         switch (confirmation.trim().toLowerCase()) {
            case "y":
            case "yes":
               overwrite = true;
               break;
         }
      } catch (InterruptedException e) {
         // ignore
      }
      return overwrite;
   }

}
