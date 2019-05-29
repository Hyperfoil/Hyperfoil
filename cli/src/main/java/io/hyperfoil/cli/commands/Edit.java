package io.hyperfoil.cli.commands;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class Edit extends BenchmarkCommand {
   private static final String EDITOR;

   static {
      String editor = System.getenv("VISUAL");
      if (editor == null || editor.isEmpty()) {
         editor = System.getenv("EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = fromCommand("update-alternatives", "--display", "editor");
      }
      if (editor == null || editor.isEmpty()) {
         editor = fromCommand("git", "var", "GIT_EDITOR");
      }
      if (editor == null || editor.isEmpty()) {
         editor = "vi";
      }
      EDITOR = editor;
   }

   private static String fromCommand(String... command) {
      String editor = null;
      try {
         Process gitEditor = new ProcessBuilder(command).start();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(gitEditor.getInputStream()))) {
            editor = reader.readLine();
         }
         gitEditor.destroy();
      } catch (IOException e) {
         // ignore error
      }
      return editor;
   }

   @Option(name = "editor", shortName = 'e', description = "Editor used.")
   private String editor;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      File sourceFile;
      try {
         Benchmark benchmark = benchmarkRef.get();
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
      Benchmark updated;
      for (;;) {
         Process process = null;
         try {
            invocation.println("Press Ctrl+C when done with edits...");
            String editor = this.editor == null ? EDITOR : this.editor;
            ArrayList<String> command = new ArrayList<>();
            command.addAll(Arrays.asList(editor.split("[\t \n]+", 0)));
            command.add(sourceFile.getAbsolutePath());
            process = new ProcessBuilder(command.toArray(new String[0])).inheritIO().start();
            process.waitFor();
         } catch (InterruptedException e) {
            if (process != null) {
               process.destroy();
            }
         } catch (IOException e) {
            sourceFile.delete();
            throw new CommandException("Failed to invoke the editor.", e);
         }
         try {
            updated = BenchmarkParser.instance().buildBenchmark(new ByteArrayInputStream(Files.readAllBytes(sourceFile.toPath())), new LocalBenchmarkData());
            break;
         } catch (ParserException e) {
            invocation.println("ERROR: " + Util.explainCauses(e));
            invocation.println("Retry edits? [Y/n] ");
            try {
               switch (invocation.getShell().readLine().trim().toLowerCase()) {
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
         invocation.context().client().register(updated);
      } catch (RestClientException e) {
         throw new CommandException("Failed to upload the benchmark");
      }
      invocation.println("Benchmark " + updated.name() + " updated.");
      return CommandResult.SUCCESS;
   }

}
