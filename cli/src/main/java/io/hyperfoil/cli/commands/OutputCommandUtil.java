package io.hyperfoil.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.command.CommandException;

import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class OutputCommandUtil {

   public static void executeOutputCommand(HyperfoilCommandInvocation invocation, String output, boolean assumeYes)
         throws CommandException {
      if (output != null && !output.isBlank()) {
         boolean effectiveAssumeYes = assumeYes || !CliUtil.hasInteractiveTerminal();
         String yesFlag = effectiveAssumeYes ? "-y " : "";
         Path outputPath = Path.of(output);
         // Extension comes from the file name only, so dots in directory names don't count.
         // getFileName() is null for a root path (e.g. "/"), which has no file name to parse.
         Path fileNamePath = outputPath.getFileName();
         String fileName = fileNamePath == null ? "" : fileNamePath.toString();
         int dotIndex = fileName.lastIndexOf('.');
         // A directory or an extensionless name falls back to an HTML report, like on master.
         String extension = (dotIndex < 0 || Files.isDirectory(outputPath)) ? "html" : fileName.substring(dotIndex + 1);
         // Any extension that isn't a recognized export format also falls back to HTML,
         // instead of forwarding it to export and failing with "Unknown format".
         if (!"html".equalsIgnoreCase(extension) && !"json".equalsIgnoreCase(extension)
               && !"csv".equalsIgnoreCase(extension) && !"zip".equalsIgnoreCase(extension)) {
            extension = "html";
         }
         if ("html".equalsIgnoreCase(extension)) {
            invocation.executeSwitchable("report --silent " + yesFlag + "--destination " + output);
         } else {
            String destination = output;
            if ("zip".equalsIgnoreCase(extension)) {
               extension = "csv";
            } else if ("csv".equalsIgnoreCase(extension)) {
               // CSV export is actually a zip archive, save it with the correct extension.
               destination = destination.substring(0, destination.length() - extension.length()) + "zip";
            }
            invocation.executeSwitchable("export " + yesFlag + "-f " + extension + " -d " + destination);
         }
      }
   }
}
