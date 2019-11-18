package io.hyperfoil.cli.commands;

import java.io.File;
import java.util.stream.Stream;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Option;
import org.aesh.io.Resource;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;

@CommandDefinition(name = "export", description = "Export run statistics.")
public class Export extends BaseRunIdCommand {
   @Option(shortName = 'f', description = "Format in which should the statistics exported. Options are JSON (default) and CSV.", defaultValue = "JSON", completer = FormatCompleter.class)
   public String format;

   @Option(shortName = 'd', description = "Target file/directory for the output", required = true, askIfNotSet = true)
   public Resource destination;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException, InterruptedException {
      ensureConnection(invocation);
      String acceptFormat;
      String defaultFilename;
      Client.RunRef runRef = getRunRef(invocation);
      switch (format.toUpperCase()) {
         case "JSON":
            acceptFormat = "application/json";
            defaultFilename = runRef.id() + ".json";
            break;
         case "CSV":
            acceptFormat = "text/csv";
            defaultFilename = runRef.id() + ".zip";
            break;
         default:
            throw new CommandException("Unknown format '" + format + "', please use JSON or CSV");
      }
      String destinationFile = destination.toString();
      if (destination.isDirectory()) {
         destinationFile = destination + File.separator + defaultFilename;
      }
      if (destination.exists()) {
         invocation.print("File " + destinationFile + " already exists, override? [y/N] ");
         switch (invocation.getShell().readLine().trim().toLowerCase()) {
            case "y":
            case "yes":
               break;
            default:
               invocation.println("Export cancelled.");
               return CommandResult.SUCCESS;
         }
      }
      runRef.statsAll(acceptFormat, destinationFile);
      return CommandResult.SUCCESS;
   }

   public static class FormatCompleter implements OptionCompleter<CompleterInvocation> {
      @Override
      public void complete(CompleterInvocation completerInvocation) {
         Stream<String> formats = Stream.of("JSON", "CSV");
         String prefix = completerInvocation.getGivenCompleteValue();
         if (prefix != null) {
            formats = formats.filter(b -> b.startsWith(prefix));
         }
         formats.forEach(completerInvocation::addCompleterValue);
      }
   }

}
