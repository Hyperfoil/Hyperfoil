package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class WebUpload implements Command<HyperfoilCommandInvocation> {
   @Argument(description = "Argument ignored (provided only for compatibility).")
   String dummy;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) {
      WebCliContext context = (WebCliContext) invocation.context();
      CountDownLatch latch;
      synchronized (context) {
         latch = context.latch = new CountDownLatch(1);
      }
      invocation.println("__HYPERFOIL_UPLOAD_MAGIC__");
      try {
         latch.await();
      } catch (InterruptedException e) {
         // interruption is cancel
      }
      String updatedSource;
      synchronized (context) {
         context.latch = null;
         if (context.editBenchmark == null) {
            invocation.println("Edits cancelled.");
            return CommandResult.SUCCESS;
         }
         updatedSource = context.editBenchmark.toString();
         context.editBenchmark = null;
      }
      WebBenchmarkData filesData = new WebBenchmarkData();
      Benchmark benchmark;
      try {
         benchmark = BenchmarkParser.instance().buildBenchmark(updatedSource, filesData);
      } catch (ParserException | BenchmarkDefinitionException e) {
         invocation.error(e);
         return CommandResult.FAILURE;
      }
      synchronized (context) {
         latch = context.latch = new CountDownLatch(1);
      }
      invocation.println("__HYPERFOIL_BENCHMARK_FILE_LIST__");
      invocation.println(benchmark.name());
      invocation.println(""); // no version
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
