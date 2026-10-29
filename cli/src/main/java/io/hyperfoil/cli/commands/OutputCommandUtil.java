package io.hyperfoil.cli.commands;

import org.aesh.command.CommandException;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class OutputCommandUtil {

   public static void executeOutputCommand(HyperfoilCommandInvocation invocation, String output) throws CommandException {
      if (output != null && !output.isBlank()) {
         String[] extensionPart = output.split("\\.");
         String extension = extensionPart[extensionPart.length - 1];
         if ("html".equalsIgnoreCase(extension)) {
            invocation.executeSwitchable("report --silent -y --destination " + output);
         } else {
            if ("zip".equalsIgnoreCase(extension)) {
               extension = "csv";
            }
            invocation.executeSwitchable("export -y -f " + extension + " -d " + output);
         }
      }
   }
}
