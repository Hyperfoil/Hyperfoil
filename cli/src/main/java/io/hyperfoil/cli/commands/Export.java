package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;

@CommandDefinition(name = "export", description = "Export run statistics.")
public class Export extends BaseExportCommand {

   @Option(shortName = 'd', description = "Target file/directory for the output", required = true, askIfNotSet = true)
   public String destination;

   @Option(shortName = 'y', description = "Assume yes for all interactive questions.", hasValue = false)
   public boolean assumeYes;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      Client.RunRef runRef = getRunRef(invocation);
      String acceptFormat = getAcceptFormat();
      String defaultFilename = getDefaultFilename(runRef);
      String destinationFile = destination;
      Path destPath = Paths.get(destination);
      if (Files.isDirectory(destPath)) {
         destinationFile = destination + File.separator + defaultFilename;
      }
      if (Files.exists(destPath) && !assumeYes) {
         invocation.print("File " + destinationFile + " already exists, override? [y/N]: ");
         if (!readYes(invocation)) {
            invocation.println("Export cancelled.");
            return CommandResult.SUCCESS;
         }
      }
      byte[] bytes = runRef.statsAll(acceptFormat);
      try {
         Files.write(Paths.get(destinationFile), bytes);
      } catch (IOException e) {
         invocation.error("Failed to write stats into " + destinationFile);
      }
      return CommandResult.SUCCESS;
   }
}
