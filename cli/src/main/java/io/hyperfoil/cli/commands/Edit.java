package io.hyperfoil.cli.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.Benchmark;
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
      Benchmark benchmark;
      try {
         benchmark = benchmarkRef.get();
         if (benchmark.source() == null) {
            throw new CommandException("No source available for benchmark '" + benchmark.name() + "', cannot edit.");
         }
         try {
            sourceFile = File.createTempFile(benchmark.name() + "-", ".yaml");
            sourceFile.deleteOnExit();
            Files.write(sourceFile.toPath(), benchmark.source().getBytes(StandardCharsets.UTF_8));
         } catch (IOException e) {
            throw new CommandException("Cannot create temporary file for edits.", e);
         }
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Cannot get benchmark " + benchmarkRef.name());
      }
      long modifiedTimestamp = sourceFile.lastModified();
      Benchmark updated;
      for (; ; ) {
         try {
            execProcess(invocation, this.editor == null ? EDITOR : this.editor, sourceFile.getAbsolutePath());
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
         } catch (ParserException e) {
            invocation.println("ERROR: " + Util.explainCauses(e));
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
            invocation.println("ERROR: " + Util.explainCauses(e));
            throw new CommandException("Failed to load the benchmark.", e);
         }
      }
      sourceFile.delete();
      try {
         invocation.context().client().register(updated, benchmark.version());
      } catch (RestClientException e) {
         if (e.getCause() instanceof Client.EditConflictException) {
            invocation.println("Conflict: the benchmark was modified while being edited.");
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
               return CommandResult.FAILURE;
            }
         } else {
            throw new CommandException("Failed to upload the benchmark", e);
         }
      }
      invocation.println("Benchmark " + updated.name() + " updated.");
      return CommandResult.SUCCESS;
   }

}
