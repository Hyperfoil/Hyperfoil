package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.util.Util;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@CommandDefinition(name = "report", description = "Generate HTML report")
public class Report extends BaseRunIdCommand {
   private static final String TEMPLATE_URL = "https://hyperfoil.io/report-template.html";

   @Option(shortName = 's', description = "Other file (in given run) to use as report input.")
   private String source;

   @Option(shortName = 'd', description = "Destination path to the HTML report", required = true, askIfNotSet = true)
   private String destination;

   @Override
   public CommandResult execute(HyperfoilCommandInvocation invocation) throws CommandException {
      String template = null;
      File templateCache = new File("./.report-template.html.cached");
      if (templateCache.exists() && templateCache.isFile()) {
         try {
            template = new String(Files.readAllBytes(templateCache.toPath()), StandardCharsets.UTF_8);
         } catch (IOException e) {
            throw new CommandException("Cannot read cached report template: ", e);
         }
      }
      if (template == null) {
         template = fetchTemplate(invocation);
         if (template == null) {
            return CommandResult.FAILURE;
         }
         try {
            Files.write(templateCache.toPath(), template.getBytes(StandardCharsets.UTF_8));
         } catch (IOException e) {
            throw new CommandException("Cannot store cached report template: ", e);
         }
      }
      Client.RunRef runRef = getRunRef(invocation);
      File statsFile;
      try {
         statsFile = File.createTempFile("stats-" + runRef.id(), ".json");
         statsFile.deleteOnExit();
      } catch (IOException e) {
         throw new CommandException("Failed to create temporary file for stats.json");
      }
      String json;
      if (source == null || source.isEmpty()) {
         try {
            runRef.statsAll("application/json", statsFile.getPath());
            json = new String(Files.readAllBytes(statsFile.toPath()), StandardCharsets.UTF_8);
         } catch (RestClientException e) {
            throw new CommandException("Failed to download statistics: ", e);
         } catch (IOException e) {
            throw new CommandException("Failed to read statistics from file: ", e);
         }
      } else {
         try {
            json = new String(runRef.file(source), StandardCharsets.UTF_8);
         } catch (RestClientException e) {
            throw new CommandException("Failed to download file '" + source + "': ", e);
         }
      }
      File destination = new File(this.destination);
      if (destination.exists()) {
         if (destination.isFile()) {
            invocation.print("File " + destination + " already exists, overwrite? [y/N]: ");
            boolean overwrite = false;
            try {
               String confirmation = invocation.getShell().readLine();
               switch (confirmation.trim().toLowerCase()) {
                  case "y":
                  case "yes":
                     overwrite = true;
                     break;
               }
            } catch (InterruptedException e) {
               // ignore
            }
            if (!overwrite) {
               invocation.println("Cancelled. You can change destination file with '-d /path/to/reporth.html'");
               return CommandResult.SUCCESS;
            }
         }
      }
      try {
         Files.write(destination.toPath(), template.replace("[/**DATAKEY**/]", json).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot write to '" + destination.toString() + "': ", e);
      }
      openInBrowser("file://" + destination.toString());
      return CommandResult.SUCCESS;
   }

   private String fetchTemplate(HyperfoilCommandInvocation invocation) {
      HyperfoilCliContext ctx = invocation.context();
      WebClient client = WebClient.create(ctx.vertx(), new WebClientOptions().setFollowRedirects(true));
      try {
         HttpRequest<Buffer> request = client.requestAbs(HttpMethod.GET, TEMPLATE_URL);
         CompletableFuture<String> future = new CompletableFuture<>();
         ctx.vertx().runOnContext(nil -> request.send(result -> {
            if (result.succeeded()) {
               future.complete(result.result().bodyAsString());
            } else {
               future.completeExceptionally(result.cause());
            }
         }));
         return future.get(30, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
         invocation.println("Failed to fetch report template: " + Util.explainCauses(e.getCause()));
         return null;
      } catch (InterruptedException | TimeoutException e) {
         invocation.println("Failed to fetch report within 30 seconds / interrupted");
         return null;
      } finally {
         client.close();
      }
   }
}
