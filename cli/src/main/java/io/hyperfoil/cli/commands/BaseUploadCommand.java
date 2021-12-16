package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.aesh.command.CommandException;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;

public abstract class BaseUploadCommand extends ServerCommand {
   @Option(name = "print-stack-trace", hasValue = false)
   boolean printStackTrace;

   @OptionList(name = "extra-files", shortName = 'f', description = "Extra files for upload (comma-separated) in case this benchmark is a template and files won't be auto-detected. Example: --extra-files foo.txt,bar.txt")
   protected List<String> extraFiles;

   protected BenchmarkSource loadBenchmarkSource(HyperfoilCommandInvocation invocation, String benchmarkYaml, BenchmarkData data) throws CommandException {
      BenchmarkSource source;
      try {
         source = BenchmarkParser.instance().createSource(benchmarkYaml, data);
         if (source.isTemplate()) {
            invocation.println("Loaded benchmark template " + source.name + " with these parameters (with defaults): ");
            printTemplateParams(invocation, source.paramsWithDefaults);
            invocation.println("Uploading...");
         } else {
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, Collections.emptyMap());
            // Note: we are loading and serializing the benchmark here just to fail fast - actual upload
            // will be done in text+binary form to avoid the pain with syncing client and server
            try {
               Util.serialize(benchmark);
            } catch (IOException e) {
               logError(invocation, e);
               throw new CommandException("Failed to serialize the benchmark.", e);
            }
            invocation.println("Loaded benchmark " + benchmark.name() + ", uploading...");
         }
      } catch (ParserException | BenchmarkDefinitionException e) {
         logError(invocation, e);
         throw new CommandException("Failed to parse the benchmark.", e);
      }
      return source;
   }

   protected void logError(HyperfoilCommandInvocation invocation, Exception e) {
      invocation.error(e);
      if (printStackTrace) {
         invocation.printStackTrace(e);
      } else {
         invocation.println("Use --print-stack-trace to display the whole stack trace of this error.");
      }
   }
}
