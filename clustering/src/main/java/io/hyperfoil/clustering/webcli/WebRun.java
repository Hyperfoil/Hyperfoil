package io.hyperfoil.clustering.webcli;

import org.aesh.command.CommandDefinition;

import io.hyperfoil.cli.commands.Run;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;

@CommandDefinition(name = "run", description = "Starts benchmark on Hyperfoil Controller server")
public class WebRun extends Run {

   @Override
   protected boolean onMissingFile(HyperfoilCommandInvocation invocation, String file, ProvidedBenchmarkData data) {
      try {
         WebCliContext context = (WebCliContext) invocation.context();
         byte[] bytes = context.loadFile(invocation, file);
         data.files.put(file, bytes);
         return true;
      } catch (InterruptedException interruptedException) {
         invocation.warn("Cancelled, not running anything.");
         return false;
      }
   }
}
