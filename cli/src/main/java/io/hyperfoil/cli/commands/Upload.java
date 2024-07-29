package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.completer.FileOptionCompleter;
import org.aesh.command.option.Argument;

import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.impl.LocalBenchmarkData;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class Upload extends BaseUploadCommand {

   @Argument(description = "YAML benchmark definition file", required = true, completer = FileOptionCompleter.class)
   String benchmarkPath;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      if (benchmarkPath != null && (benchmarkPath.startsWith("http://") || benchmarkPath.startsWith("https://"))) {
         LocalBenchmarkData extraData = loadExtras(invocation, null);
         if (extraData == null) {
            return CommandResult.FAILURE;
         }
         BenchmarkSource source = loadFromUrl(invocation, benchmarkPath, extraData.files());
         if (source == null) {
            return CommandResult.FAILURE;
         }
         Client.BenchmarkRef benchmarkRef = invocation.context().client().register(source.yaml, source.data.files(), null,
               null);
         invocation.context().setServerBenchmark(benchmarkRef);
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } else {
         return uploadFromFile(invocation);
      }
   }

   private LocalBenchmarkData loadExtras(HyperfoilCommandInvocation invocation, Path o) {
      LocalBenchmarkData extraData = new LocalBenchmarkData(o);
      if (extraFiles != null) {
         for (String extraFile : extraFiles) {
            try (InputStream ignored = extraData.readFile(extraFile)) {
               // intentionally empty
            } catch (IOException e) {
               invocation.error("Cannot read file " + extraFile, e);
               return null;
            }
         }
      }
      return extraData;
   }

   private CommandResult uploadFromFile(HyperfoilCommandInvocation invocation) throws CommandException {
      String sanitizedPath = CliUtil.sanitize(benchmarkPath);
      String benchmarkYaml;
      try {
         benchmarkYaml = Files.readString(Path.of(sanitizedPath));
      } catch (IOException e) {
         logError(invocation, e);
         throw new CommandException("Failed to load the benchmark.", e);
      }
      LocalBenchmarkData data = loadExtras(invocation, Paths.get(sanitizedPath).toAbsolutePath());
      if (data == null) {
         return CommandResult.FAILURE;
      }

      try {
         BenchmarkSource source = loadBenchmarkSource(invocation, benchmarkYaml, data);

         Path benchmarkDir = Paths.get(sanitizedPath).toAbsolutePath().getParent();
         Map<String, Path> extraFiles = source.data.files().keySet().stream()
               .collect(Collectors.toMap(file -> file, file -> {
                  Path path = Paths.get(file);
                  return path.isAbsolute() ? path : benchmarkDir.resolve(file);
               }));
         HyperfoilCliContext ctx = invocation.context();
         Client.BenchmarkRef benchmarkRef = ctx.client().register(
               Paths.get(sanitizedPath).toAbsolutePath(), extraFiles, null, null);
         ctx.setServerBenchmark(benchmarkRef);
         invocation.println("... done.");
         return CommandResult.SUCCESS;
      } catch (RestClientException e) {
         invocation.error(e);
         throw new CommandException("Failed to upload the benchmark.", e);
      } catch (CommandException e) {
         throw e;
      } catch (Exception e) {
         logError(invocation, e);
         throw new CommandException("Unknown error.", e);
      }
   }
}
