package io.hyperfoil.clustering.webcli;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.commands.BaseUploadCommand;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;

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
      String updatedSource = upload(invocation, context);
      if (updatedSource == null) {
         return CommandResult.FAILURE;
      }
      Map<String, byte[]> extraData = new HashMap<>();
      if (extraFiles != null) {
         for (String extraFile : extraFiles) {
            try {
               extraData.put(extraFile, context.loadFile(invocation, extraFile));
            } catch (InterruptedException e) {
               invocation.println("Benchmark upload cancelled.");
               return CommandResult.FAILURE;
            }
         }
      }
      ProvidedBenchmarkData data = new ProvidedBenchmarkData(extraData);
      for (;;) {
         BenchmarkSource source;
         try {
            source = loadBenchmarkSource(invocation, updatedSource, data);
         } catch (CommandException e) {
            throw e;
         } catch (BenchmarkData.MissingFileException e) {
            try {
               data.files().put(e.file, context.loadFile(invocation, e.file));
               continue;
            } catch (InterruptedException interruptedException) {
               invocation.println("Benchmark upload cancelled.");
               return CommandResult.FAILURE;
            }
         } catch (Exception e) {
            logError(invocation, e);
            return CommandResult.FAILURE;
         }
         context.setServerBenchmark(context.client().register(source.yaml, data.files(), null, null));
         invocation.println("Benchmark " + source.name + " uploaded.");
         return CommandResult.SUCCESS;
      }
   }

   private String upload(HyperfoilCommandInvocation invocation, WebCliContext context) {
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
      synchronized (context) {
         context.latch = null;
         if (context.editBenchmark == null) {
            invocation.println("Upload cancelled.");
            return null;
         }
         String updatedSource = context.editBenchmark.toString();
         context.editBenchmark = null;
         return updatedSource;
      }
   }

}
