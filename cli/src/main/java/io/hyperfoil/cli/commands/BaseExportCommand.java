package io.hyperfoil.cli.commands;

import java.util.stream.Stream;

import org.aesh.command.CommandException;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Option;

import io.hyperfoil.controller.Client;

public abstract class BaseExportCommand extends BaseRunIdCommand {
   @Option(shortName = 'f', description = "Format in which should the statistics exported. Options are JSON (default) and CSV.", defaultValue = "JSON", completer = FormatCompleter.class)
   public String format;

   protected String getDefaultFilename(Client.RunRef runRef) throws CommandException {
      switch (format.toUpperCase()) {
         case "JSON":
            return runRef.id() + ".json";
         case "CSV":
            return runRef.id() + ".zip";
         default:
            throw new CommandException("Unknown format '" + format + "', please use JSON or CSV");
      }
   }

   protected String getAcceptFormat() throws CommandException {
      switch (format.toUpperCase()) {
         case "JSON":
            return "application/json";
         case "CSV":
            return "application/zip";
         default:
            throw new CommandException("Unknown format '" + format + "', please use JSON or CSV");
      }
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
