package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class Edit extends BaseEditCommand {
   private static final Path SKIP = Paths.get(".SKIP");

   @Option(name = "editor", shortName = 'e', description = "Editor used.")
   private String editor;

   @SuppressWarnings("ResultOfMethodCallIgnored")
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      Client.BenchmarkSource source = ensureSource(invocation, benchmarkRef);
      String updatedName;
      File sourceFile;
      Map<String, Path> filesToUpload = new HashMap<>();
      try {
         sourceFile = File.createTempFile(benchmarkRef.name() + "-", ".yaml");
         Files.write(sourceFile.toPath(), source.source.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot create temporary file for edits.", e);
      }
      long modifiedTimestamp = sourceFile.lastModified();
      for (; ; ) {
         try {
            CliUtil.execProcess(invocation, true, this.editor == null ? EDITOR : this.editor, sourceFile.getAbsolutePath());
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
         if (extraFiles != null) {
            for (String extraFile : extraFiles) {
               filesToUpload.put(extraFile, Path.of(extraFile));
            }
         }
         BenchmarkData askingData = new AskingBenchmarkData(invocation, cancelled, filesToUpload);
         try {
            byte[] updatedSource = Files.readAllBytes(sourceFile.toPath());
            BenchmarkSource newSource = BenchmarkParser.instance().createSource(new String(updatedSource, StandardCharsets.UTF_8), askingData);
            if (!newSource.isTemplate()) {
               Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(newSource, Collections.emptyMap());
               try {
                  Util.serialize(benchmark);
               } catch (IOException e) {
                  invocation.error("Benchmark is not serializable.", e);
                  sourceFile.delete();
                  // This is a bug in Hyperfoil; there isn't anything the user could do about that (no need to retry).
                  return CommandResult.FAILURE;
               }
            }
            updatedName = newSource.name;
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
         if (!updatedName.equals(benchmarkRef.name())) {
            invocation.println("NOTE: Renamed benchmark " + benchmarkRef.name() + " to " + updatedName + "; old benchmark won't be deleted.");
            prevVersion = null;
         }
         invocation.println("Uploading benchmark " + updatedName + "...");
         filesToUpload.entrySet().removeIf(entry -> entry.getValue() == SKIP);
         invocation.context().client().register(sourceFile.toPath(), filesToUpload, prevVersion, benchmarkRef.name());
         sourceFile.delete();
      } catch (RestClientException e) {
         if (e.getCause() instanceof Client.EditConflictException) {
            switch (askForConflictResolution(invocation)) {
               case CANCEL:
                  invocation.println("You can find your edits in " + sourceFile);
                  return CommandResult.SUCCESS;
               case RETRY:
                  try {
                     invocation.executeCommand("edit " + this.benchmark + (editor == null ? "" : " -e " + editor));
                  } catch (Exception ex) {
                     // who cares
                  }
                  return CommandResult.SUCCESS;
               case OVERWRITE:
                  invocation.context().client().register(sourceFile.toPath(), filesToUpload, null, benchmarkRef.name());
            }
         } else {
            invocation.println(Util.explainCauses(e));
            invocation.println("You can find your edits in " + sourceFile);
            throw new CommandException("Failed to upload the benchmark", e);
         }
      }
      invocation.println("Benchmark " + updatedName + " updated.");
      return CommandResult.SUCCESS;
   }

   private static class AskingBenchmarkData implements BenchmarkData {
      private final AtomicBoolean cancelled;
      private final Map<String, Path> filesToUpload;
      private final HyperfoilCommandInvocation invocation;

      public AskingBenchmarkData(HyperfoilCommandInvocation invocation, AtomicBoolean cancelled, Map<String, Path> filesToUpload) {
         this.cancelled = cancelled;
         this.filesToUpload = filesToUpload;
         this.invocation = invocation;
      }

      @Override
      public InputStream readFile(String file) {
         if (cancelled.get() || filesToUpload.containsKey(file)) {
            return EMPTY_INPUT_STREAM;
         }
         File ff = new File(file);
         try {
            if (ff.exists()) {
               invocation.print("Re-upload file " + file + "? [y/N] ");
               switch (invocation.inputLine().trim().toLowerCase()) {
                  case "y":
                  case "yes":
                     filesToUpload.put(file, ff.toPath());
                     break;
                  default:
                     filesToUpload.put(file, SKIP);
               }
            } else if (!ff.isAbsolute()) {
               invocation.println("Non-absolute path " + file + ", set absolute path or leave empty to skip: ");
               for (; ; ) {
                  String path = invocation.inputLine().trim();
                  if (path.isEmpty()) {
                     invocation.println("Ignoring file " + file + ".");
                     break;
                  }
                  ff = new File(path);
                  if (!ff.exists()) {
                     invocation.println("Invalid path " + path + ", retry or leave empty to skip: ");
                  } else {
                     filesToUpload.put(file, ff.toPath());
                     break;
                  }
               }
            } else {
               invocation.println("Ignoring file " + file + " as it doesn't exist on local file system.");
            }
         } catch (InterruptedException ie) {
            cancelled.set(true);
            throw new BenchmarkDefinitionException("interrupted");
         }
         // The benchmark is ignored, does not need to read the file
         return EMPTY_INPUT_STREAM;
      }

      @Override
      public Map<String, byte[]> files() {
         // not used
         return Collections.emptyMap();
      }
   }
}
