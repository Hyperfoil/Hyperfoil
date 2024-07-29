package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class Edit extends BaseEditCommand {

   @Option(name = "editor", shortName = 'e', description = "Editor used.")
   private String editor;

   @SuppressWarnings("ResultOfMethodCallIgnored")
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      Client.BenchmarkSource source = ensureSource(invocation, benchmarkRef);
      File sourceFile;
      try {
         sourceFile = File.createTempFile(benchmarkRef.name() + "-", ".yaml");
         Files.write(sourceFile.toPath(), source.source.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot create temporary file for edits.", e);
      }
      long modifiedTimestamp = sourceFile.lastModified();
      Map<String, byte[]> extraData = new HashMap<>();
      if (extraFiles != null) {
         for (String extraFile : extraFiles) {
            try {
               extraData.put(extraFile, Files.readAllBytes(Path.of(extraFile)));
            } catch (IOException e) {
               invocation.error(
                     "Cannot read file " + extraFile + " (current directory is " + new File("").getAbsolutePath() + ")", e);
            }
         }
      }
      ProvidedBenchmarkData data = new ProvidedBenchmarkData(extraData);
      BenchmarkSource newSource;
      for (;;) {
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
         try {
            byte[] updatedSource = Files.readAllBytes(sourceFile.toPath());
            newSource = BenchmarkParser.instance().createSource(new String(updatedSource, StandardCharsets.UTF_8), data);
            if (!newSource.isTemplate()) {
               Benchmark benchmark;
               for (;;) {
                  try {
                     benchmark = BenchmarkParser.instance().buildBenchmark(newSource, Collections.emptyMap());
                     break;
                  } catch (BenchmarkData.MissingFileException e) {
                     Path path = Path.of("<none>");
                     try {
                        path = CliUtil.getLocalFileForUpload(invocation, e.file);
                        if (path != null) {
                           data.files().put(e.file, Files.readAllBytes(path));
                        } else {
                           data.ignoredFiles.add(e.file);
                        }
                     } catch (InterruptedException e2) {
                        invocation.println("Edits cancelled.");
                        sourceFile.delete();
                        return CommandResult.FAILURE;
                     } catch (IOException e2) {
                        invocation.error("Cannot read file " + path, e2);
                     }
                  }
               }
               try {
                  Util.serialize(benchmark);
               } catch (IOException e) {
                  invocation.error("Benchmark is not serializable.", e);
                  sourceFile.delete();
                  // This is a bug in Hyperfoil; there isn't anything the user could do about that (no need to retry).
                  return CommandResult.FAILURE;
               }
            }
            break;
         } catch (ParserException | BenchmarkDefinitionException e) {
            invocation.error(e);
            invocation.print("Retry edits? [Y/n] ");
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
            data = new ProvidedBenchmarkData(extraData);
         } catch (IOException e) {
            invocation.error(e);
            throw new CommandException("Failed to load the benchmark.", e);
         }
      }
      try {
         String prevVersion = source.version;
         if (!newSource.name.equals(benchmarkRef.name())) {
            invocation.println("NOTE: Renamed benchmark " + benchmarkRef.name() + " to " + newSource.name
                  + "; old benchmark won't be deleted.");
            prevVersion = null;
         }
         invocation.println("Uploading benchmark " + newSource.name + "...");
         invocation.context().client().register(newSource.yaml, data.files(), prevVersion, benchmarkRef.name());
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
                  invocation.context().client().register(newSource.yaml, data.files(), null, benchmarkRef.name());
            }
         } else {
            invocation.println(Util.explainCauses(e));
            invocation.println("You can find your edits in " + sourceFile);
            throw new CommandException("Failed to upload the benchmark", e);
         }
      }
      invocation.println("Benchmark " + newSource.name + " updated.");
      return CommandResult.SUCCESS;
   }

}
