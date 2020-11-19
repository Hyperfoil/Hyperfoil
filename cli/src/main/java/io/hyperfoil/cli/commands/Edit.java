package io.hyperfoil.cli.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class Edit extends BenchmarkCommand {

   @Option(name = "editor", shortName = 'e', description = "Editor used.")
   private String editor;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      File sourceFile;
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
      try {
         sourceFile = File.createTempFile(benchmarkRef.name() + "-", ".yaml");
         Files.write(sourceFile.toPath(), source.source.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot create temporary file for edits.", e);
      }
      long modifiedTimestamp = sourceFile.lastModified();
      Benchmark updated;
      for (; ; ) {
         try {
            execProcess(invocation, true, this.editor == null ? EDITOR : this.editor, sourceFile.getAbsolutePath());
         } catch (IOException e) {
            sourceFile.delete();
            throw new CommandException("Failed to invoke the editor.", e);
         }
         if (sourceFile.lastModified() == modifiedTimestamp) {
            invocation.println("No changes, not uploading.");
            sourceFile.delete();
            return CommandResult.SUCCESS;
         }
         try {
            updated = BenchmarkParser.instance().buildBenchmark(new ByteArrayInputStream(Files.readAllBytes(sourceFile.toPath())), new LocalBenchmarkData());
            break;
         } catch (ParserException | BenchmarkDefinitionException e) {
            invocation.error(e);
            invocation.println("Retry edits? [Y/n] ");
            try {
               switch (invocation.inputLine().trim().toLowerCase()) {
                  case "n":
                  case "no":
                     return CommandResult.FAILURE;
               }
            } catch (InterruptedException ie) {
               invocation.println("Edits cancelled.");
               sourceFile.delete();
               return CommandResult.FAILURE;
            }
         } catch (IOException e) {
            invocation.error(e);
            throw new CommandException("Failed to load the benchmark.", e);
         }
      }
      try {
         String prevVersion = source.version;
         if (!updated.name().equals(benchmarkRef.name())) {
            invocation.println("NOTE: Renamed benchmark " + benchmarkRef.name() + " to " + updated.name() + "; old benchmark won't be deleted.");
            prevVersion = null;
         }
         invocation.println("Uploading benchmark " + updated.name() + "...");
         invocation.context().client().register(sourceFile.getAbsolutePath(), new ArrayList<>(updated.files().keySet()), prevVersion);
         sourceFile.delete();
      } catch (RestClientException e) {
         if (e.getCause() instanceof Client.EditConflictException) {
            invocation.println("Conflict: the benchmark was modified while being edited.");
            invocation.println("You can find your edits in " + sourceFile);
            invocation.print("Options: [C]ancel edit, [r]etry edits, [o]verwrite: ");
            try {
               switch (invocation.inputLine().trim().toLowerCase()) {
                  case "":
                  case "c":
                     invocation.println("Edit cancelled.");
                     return CommandResult.SUCCESS;
                  case "r":
                     try {
                        invocation.executeCommand("edit " + this.benchmark + (editor == null ? "" : " -e " + editor));
                     } catch (Exception ex) {
                        // who cares
                     }
                     return CommandResult.SUCCESS;
                  case "o":
                     invocation.context().client().register(updated, null);
               }
            } catch (InterruptedException ie) {
               invocation.println("Edit cancelled by interrupt.");
               sourceFile.delete();
               return CommandResult.FAILURE;
            }
         } else {
            invocation.println(Util.explainCauses(e));
            invocation.println("You can find your edits in " + sourceFile);
            throw new CommandException("Failed to upload the benchmark", e);
         }
      }
      invocation.println("Benchmark " + updated.name() + " updated.");
      return CommandResult.SUCCESS;
   }

}
