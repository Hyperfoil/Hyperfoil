package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.io.Resource;

import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.impl.Util;

@CommandDefinition(name = "upload", description = "Uploads benchmark definition to Hyperfoil Controller server")
public class Upload extends BaseUploadCommand {

   @Argument(description = "YAML benchmark definition file", required = true)
   Resource benchmarkResource;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      ensureConnection(invocation);
      HyperfoilCliContext ctx = invocation.context();
      Resource sanitizedResource = CliUtil.sanitize(benchmarkResource);
      String benchmarkYaml;
      try {
         benchmarkYaml = Util.toString(sanitizedResource.read());
      } catch (IOException e) {
         logError(invocation, e);
         throw new CommandException("Failed to load the benchmark.", e);
      }
      LocalBenchmarkData data = new LocalBenchmarkData(Paths.get(sanitizedResource.getAbsolutePath()));
      if (extraFiles != null) {
         for (String extraFile : extraFiles) {
            try (InputStream ignored = data.readFile(extraFile)) {
               // intentionally empty
            } catch (IOException e) {
               invocation.error("Cannot read file " + extraFile, e);
               return CommandResult.FAILURE;
            }
         }
      }

      try {
         BenchmarkSource source = loadBenchmarkSource(invocation, benchmarkYaml, data);

         Path benchmarkDir = Paths.get(sanitizedResource.getAbsolutePath()).getParent();
         Map<String, Path> extraFiles = source.data.files().keySet().stream()
               .collect(Collectors.toMap(file -> file, file -> {
                  Path path = Paths.get(file);
                  return path.isAbsolute() ? path : benchmarkDir.resolve(file);
               }));
         Client.BenchmarkRef benchmarkRef = ctx.client().register(
               Paths.get(sanitizedResource.getAbsolutePath()), extraFiles, null, null);
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
