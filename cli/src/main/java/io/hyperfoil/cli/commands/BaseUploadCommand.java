package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aesh.command.CommandException;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public abstract class BaseUploadCommand extends ServerCommand {
   @Option(name = "print-stack-trace", hasValue = false)
   boolean printStackTrace;

   @OptionList(name = "extra-files", shortName = 'f', description = "Extra files for upload (comma-separated) in case this benchmark is a template and files won't be auto-detected. Example: --extra-files foo.txt,bar.txt")
   protected List<String> extraFiles;

   protected BenchmarkSource loadBenchmarkSource(HyperfoilCommandInvocation invocation, String benchmarkYaml,
         BenchmarkData data) throws CommandException {
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

   protected BenchmarkSource loadFromUrl(HyperfoilCommandInvocation invocation, String benchmarkPath,
         Map<String, byte[]> extraData) throws CommandException {
      WebClientOptions options = new WebClientOptions().setFollowRedirects(false);
      if (benchmarkPath.startsWith("https://")) {
         options.setSsl(true).setUseAlpn(true);
      }
      HyperfoilCliContext ctx = invocation.context();
      WebClient client = WebClient.create(ctx.vertx(), options);
      try {
         HttpResponse<Buffer> response = client.getAbs(benchmarkPath).send().toCompletionStage().toCompletableFuture().get(15,
               TimeUnit.SECONDS);
         String benchmarkYaml = response.bodyAsString();
         ProvidedBenchmarkData data = new ProvidedBenchmarkData();
         data.files().putAll(extraData);

         URL url = new URL(benchmarkPath);
         String path = url.getPath();
         if (path != null && path.contains("/")) {
            path = path.substring(0, path.lastIndexOf('/') + 1);
         } else {
            path = "/";
         }
         URL dirUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), path);

         for (;;) {
            try {
               return loadBenchmarkSource(invocation, benchmarkYaml, data);
            } catch (BenchmarkData.MissingFileException e) {
               try {
                  HttpResponse<Buffer> fileResponse = client.getAbs(dirUrl + e.file).send().toCompletionStage()
                        .toCompletableFuture().get(15, TimeUnit.SECONDS);
                  byte[] bytes = fileResponse.bodyAsBuffer().getBytes();
                  data.files.put(e.file, bytes);
               } catch (ExecutionException e2) {
                  invocation.error("Download of " + e.file + " failed", e2);
                  return null;
               } catch (TimeoutException e2) {
                  invocation.error("Download of " + e.file + " timed out after 15 seconds", null);
                  return null;
               }
            }
         }
      } catch (InterruptedException e) {
         invocation.println("Benchmark download cancelled.");
      } catch (ExecutionException e) {
         invocation.error("Benchmark download failed", e);
      } catch (TimeoutException e) {
         invocation.error("Benchmark download timed out after 15 seconds");
      } catch (MalformedURLException e) {
         invocation.error("Cannot parse URL " + benchmarkPath);
      } finally {
         client.close();
      }
      return null;
   }
}
