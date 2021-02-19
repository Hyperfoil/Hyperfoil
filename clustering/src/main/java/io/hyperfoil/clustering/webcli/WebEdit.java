package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.commands.BaseEditCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

@CommandDefinition(name = "edit", description = "Edit benchmark definition.")
public class WebEdit extends BaseEditCommand {
   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      Client.BenchmarkRef benchmarkRef = ensureBenchmark(invocation);
      Client.BenchmarkSource source = ensureSource(invocation, benchmarkRef);
      WebCliContext context = (WebCliContext) invocation.context();
      WebBenchmarkData filesData = new WebBenchmarkData();
      Benchmark updated;
      String updatedSource = source.source;
      for (; ; ) {
         CountDownLatch latch;
         synchronized (context) {
            latch = context.latch = new CountDownLatch(1);
         }
         invocation.println("__HYPERFOIL_EDIT_MAGIC__");
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
            updated = BenchmarkParser.instance().buildBenchmark(updatedSource, filesData);
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
         }
      }
      String prevVersion = source.version;
      if (!updated.name().equals(benchmarkRef.name())) {
         invocation.println("NOTE: Renamed benchmark " + benchmarkRef.name() + " to " + updated.name() + "; old benchmark won't be deleted.");
         prevVersion = null;
      }
      CountDownLatch latch;
      synchronized (context) {
         latch = context.latch = new CountDownLatch(1);
      }
      invocation.println("__HYPERFOIL_BENCHMARK_FILE_LIST__");
      invocation.println(benchmark);
      invocation.println(prevVersion == null ? "" : prevVersion);
      for (String file : filesData.files) {
         invocation.println(file);
      }
      invocation.println("__HYPERFOIL_BENCHMARK_END_OF_FILES__");
      try {
         latch.await();
      } catch (InterruptedException e) {
      }
      return CommandResult.SUCCESS;
   }

}
