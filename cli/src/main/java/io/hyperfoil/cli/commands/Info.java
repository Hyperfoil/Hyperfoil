package io.hyperfoil.cli.commands;

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
import io.hyperfoil.client.Client;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.core.util.Util;

@CommandDefinition(name = "info", description = "Provides information about the benchmark.")
public class Info extends BenchmarkCommand {
   @Option(name = "pager", shortName = 'p', description = "Pager used.")
   private String pager;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      try {
         Benchmark benchmark = benchmarkRef.get();
         if (benchmark.source() == null) {
            invocation.println("No source available for benchmark '" + benchmark.name() + "'.");
         } else {
            File sourceFile;
            try {
               sourceFile = File.createTempFile(benchmark.name() + "-", ".yaml");
               sourceFile.deleteOnExit();
               Files.write(sourceFile.toPath(), benchmark.source().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
               throw new CommandException("Cannot create temporary file for edits.", e);
            }
            try {
               execProcess(invocation, pager == null ? PAGER : pager, sourceFile.getPath());
            } catch (IOException e) {
               throw new CommandException("Cannot open file " + sourceFile, e);
            } finally {
               sourceFile.delete();
            }
         }
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.println("ERROR: " + Util.explainCauses(e));
         throw new CommandException("Cannot get benchmark " + benchmarkRef.name());
      }
   }
}
