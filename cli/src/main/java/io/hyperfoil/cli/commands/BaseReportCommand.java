package io.hyperfoil.cli.commands;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aesh.command.CommandException;
import org.aesh.command.option.Option;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClientException;
import io.hyperfoil.controller.Client;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public abstract class BaseReportCommand extends BaseRunIdCommand {
   private static final String TEMPLATE_URL = "https://hyperfoil.io/report-template.html";
   @Option(shortName = 's', description = "Other file (in given run) to use as report input.")
   private String source;

   protected String getReport(HyperfoilCommandInvocation invocation) throws CommandException {
      String template = null;
      File templateCache = new File("./.report-template.html.cached");
      if (templateCache.exists() && templateCache.isFile()) {
         try {
            template = Files.readString(templateCache.toPath(), StandardCharsets.UTF_8);
         } catch (IOException e) {
            throw new CommandException("Cannot read cached report template: ", e);
         }
      }
      if (template == null) {
         template = fetchTemplate(invocation);
         try {
            Files.write(templateCache.toPath(), template.getBytes(StandardCharsets.UTF_8));
         } catch (IOException e) {
            throw new CommandException("Cannot store cached report template: ", e);
         }
      }
      Client.RunRef runRef = getRunRef(invocation);
      String json;
      if (source == null || source.isEmpty()) {
         try {
            byte[] bytes = runRef.statsAll("application/json");
            json = new String(bytes, StandardCharsets.UTF_8);
         } catch (RestClientException e) {
            throw new CommandException("Failed to download statistics: ", e);
         }
      } else {
         try {
            json = new String(runRef.file(source), StandardCharsets.UTF_8);
         } catch (RestClientException e) {
            throw new CommandException("Failed to download file '" + source + "': ", e);
         }
      }
      return template.replace("[/**DATAKEY**/]", json);
   }

   private String fetchTemplate(HyperfoilCommandInvocation invocation) throws CommandException {
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
         throw new CommandException("Failed to fetch report template: ", e.getCause());
      } catch (InterruptedException | TimeoutException e) {
         throw new CommandException("Failed to fetch report within 30 seconds / interrupted");
      } finally {
         client.close();
      }
   }
}
