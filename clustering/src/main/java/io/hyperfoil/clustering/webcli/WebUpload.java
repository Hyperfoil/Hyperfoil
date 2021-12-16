package io.hyperfoil.clustering.webcli;

import java.util.concurrent.CountDownLatch;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.commands.BaseUploadCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class WebUpload extends BaseUploadCommand {
   @Argument(description = "Argument ignored (provided only for compatibility).")
   String dummy;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      if (dummy != null && !dummy.isEmpty()) {
         invocation.println("Argument '" + dummy + "' ignored: you must open file dialogue in WebCLI using the button below.");
      }
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
            invocation.println("Upload cancelled.");
            return CommandResult.SUCCESS;
         }
         updatedSource = context.editBenchmark.toString();
         context.editBenchmark = null;
      }
      WebBenchmarkData filesData = new WebBenchmarkData();
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
      for (; ; ) {
         BenchmarkSource source;
         try {
            source = loadBenchmarkSource(invocation, updatedSource, filesData);
         } catch (CommandException e) {
            throw e;
         } catch (MissingFileException e) {
            try {
               filesData.loadFile(invocation, context, e.file);
               continue;
            } catch (InterruptedException interruptedException) {
               invocation.println("Benchmark upload cancelled.");
               return CommandResult.FAILURE;
            }
         } catch (Exception e) {
            logError(invocation, e);
            return CommandResult.FAILURE;
         }
         context.setServerBenchmark(context.client().register(source.yaml, filesData.files(), null, null));
         invocation.println("Benchmark " + source.name + " uploaded.");
         return CommandResult.SUCCESS;
      }
   }

}
