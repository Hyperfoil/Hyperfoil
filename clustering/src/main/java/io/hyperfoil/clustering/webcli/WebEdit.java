package io.hyperfoil.clustering.webcli;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.commands.BaseEditCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class WebEdit extends BaseEditCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      Client.BenchmarkSource source = ensureSource(invocation, benchmarkRef);
      WebCliContext context = (WebCliContext) invocation.context();
      WebBenchmarkData filesData = new WebBenchmarkData();
      for (var existing : benchmarkRef.files().entrySet()) {
         filesData.files.put(existing.getKey(), existing.getValue());
      }
      if (extraFiles != null) {
         for (String extraFile : extraFiles) {
            try {
               filesData.loadFile(invocation, context, extraFile);
            } catch (InterruptedException e) {
               invocation.println("Benchmark upload cancelled.");
               return CommandResult.FAILURE;
            }
         }
      }
      String updatedSource = source.source;
      String updatedName;
      for (; ; ) {
         CountDownLatch latch;
         synchronized (context) {
            latch = context.latch = new CountDownLatch(1);
         }
         invocation.println("__HYPERFOIL_EDIT_MAGIC__" + benchmarkRef.name());
         invocation.println(updatedSource);
         try {
            latch.await();
         } catch (InterruptedException e) {
            // interruption is cancel
         }
         synchronized (context) {
            context.latch = null;
            if (context.editBenchmark == null) {
               invocation.println("Edits cancelled.");
               return CommandResult.SUCCESS;
            }
            updatedSource = context.editBenchmark.toString();
            context.editBenchmark = null;
         }
         try {
            BenchmarkSource newSource = BenchmarkParser.instance().createSource(updatedSource, filesData);
            updatedName = newSource.name;
            if (!newSource.isTemplate()) {
               Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(newSource, Collections.emptyMap());
               try {
                  Util.serialize(benchmark);
               } catch (IOException e) {
                  invocation.error("Benchmark is not serializable.", e);
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
               return CommandResult.FAILURE;
            }
         } catch (MissingFileException e) {
            try {
               filesData.loadFile(invocation, context, e.file);
            } catch (InterruptedException interruptedException) {
               invocation.println("Edits cancelled.");
               return CommandResult.FAILURE;
            }
         }
      }
      String prevVersion = source.version;
      if (!updatedName.equals(benchmarkRef.name())) {
         invocation.println("NOTE: Renamed benchmark " + benchmarkRef.name() + " to " + updatedName + "; old benchmark won't be deleted.");
         prevVersion = null;
      }
      CountDownLatch latch;
      synchronized (context) {
         latch = context.latch = new CountDownLatch(1);
      }
      invocation.println("__HYPERFOIL_BENCHMARK_FILE_LIST__");
      invocation.println(benchmark);
      invocation.println(prevVersion == null ? "" : prevVersion);
      for (String file : filesData.files.keySet()) {
         invocation.println(file);
      }
      invocation.println("__HYPERFOIL_BENCHMARK_END_OF_FILES__");
      try {
         latch.await();
      } catch (InterruptedException e) {
      }
      context.latch = null;
      return CommandResult.SUCCESS;
   }

}
