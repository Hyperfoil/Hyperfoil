package io.hyperfoil.cli.commands;

import java.util.List;

import org.aesh.command.CommandException;
import org.aesh.command.option.OptionList;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;

public abstract class BaseEditCommand extends BenchmarkCommand {

   @OptionList(name = "extra-files", shortName = 'f', description = "Extra files for upload (comma-separated) in case this benchmark is a template and files won't be auto-detected. Example: --extra-files foo.txt,bar.txt")
   protected List<String> extraFiles;

   protected ConflictResolution askForConflictResolution(HyperfoilCommandInvocation invocation) {
      invocation.println("Conflict: the benchmark was modified while being edited.");
      try {
         for (;;) {
            invocation.print("Options: [C]ancel edit, [r]etry edits, [o]verwrite: ");
            switch (invocation.inputLine().trim().toLowerCase()) {
               case "":
               case "c":
                  invocation.println("Edit cancelled.");
                  return ConflictResolution.CANCEL;
               case "r":
                  return ConflictResolution.RETRY;
               case "o":
                  return ConflictResolution.OVERWRITE;
            }
         }
      } catch (InterruptedException ie) {
         invocation.println("Edit cancelled by interrupt.");
         return ConflictResolution.CANCEL;
      }
   }

   protected Client.BenchmarkSource ensureSource(HyperfoilCommandInvocation invocation, Client.BenchmarkRef benchmarkRef)
         throws CommandException {
      Client.BenchmarkSource source;
      try {
         source = benchmarkRef.source();
         if (source == null) {
            throw new CommandException("No source available for benchmark '" + benchmarkRef.name() + "', cannot edit.");
         }
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Cannot get benchmark " + benchmarkRef.name());
      }
      if (source.version == null) {
         invocation.warn("Server did not send benchmark source version, modification conflicts won't be prevented.");
      }
      return source;
   }

   protected enum ConflictResolution {
      CANCEL,
      RETRY,
      OVERWRITE,
   }
}
