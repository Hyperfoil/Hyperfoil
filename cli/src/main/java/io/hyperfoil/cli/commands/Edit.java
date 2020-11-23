package io.hyperfoil.cli.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class Edit extends BenchmarkCommand {

   private static final InputStream EMPTY_INPUT_STREAM = new InputStream() {
      @Override
      public int read() {
         return -1;
      }
   };
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
      Map<String, Boolean> filesToUpload = new HashMap<>();
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
         AtomicBoolean cancelled = new AtomicBoolean(false);
         filesToUpload.clear();
         BenchmarkData askingData = new BenchmarkData() {
            @Override
            public InputStream readFile(String file) {
               if (cancelled.get() || filesToUpload.containsKey(file)) {
                  return EMPTY_INPUT_STREAM;
               } else if (new File(file).exists()) {
                  invocation.print("Re-upload file " + file + "? [y/N] ");
                  try {
                     switch (invocation.inputLine().trim().toLowerCase()) {
                        case "y":
                        case "yes":
                           filesToUpload.put(file, Boolean.TRUE);
                           break;
                        default:
                           filesToUpload.put(file, Boolean.FALSE);
                     }
                  } catch (InterruptedException ie) {
                     cancelled.set(true);
                     throw new BenchmarkDefinitionException("interrupted");
                  }
               } else {
                  invocation.println("Ignoring file " + file + " as it doesn't exist on local file system.");
               }
               // The benchmark is ignored, does not need to read the file
               return EMPTY_INPUT_STREAM;
            }

            @Override
            public Map<String, byte[]> files() {
               // not used
               return Collections.emptyMap();
            }
         };
         try {
            updated = BenchmarkParser.instance().buildBenchmark(new ByteArrayInputStream(Files.readAllBytes(sourceFile.toPath())), askingData);
            break;
         } catch (ParserException | BenchmarkDefinitionException e) {
            if (cancelled.get()) {
               invocation.println("Edits cancelled.");
               sourceFile.delete();
               return CommandResult.FAILURE;
            }
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
         List<String> fileList = filesToUpload.entrySet().stream()
               .filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
         invocation.context().client().register(sourceFile.getAbsolutePath(), fileList, prevVersion, true);
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
