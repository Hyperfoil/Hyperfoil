package io.hyperfoil.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.Client;
import io.hyperfoil.core.util.Util;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpResponse;

class BenchmarkRefImpl implements Client.BenchmarkRef {
   private final RestClient client;
   private final String name;

   public BenchmarkRefImpl(RestClient client, String name) {
      this.client = client;
      this.name = name;
   }

   @Override
   public String name() {
      return name;
   }

   @Override
   public Client.BenchmarkSource source() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/benchmark/" + encode(name))
                  .putHeader(HttpHeaders.ACCEPT.toString(), "text/vnd.yaml")
                  .send(handler), 0,
            response -> {
               if (response.statusCode() == 200) {
                  return new Client.BenchmarkSource(response.bodyAsString(), response.getHeader(HttpHeaders.ETAG.toString()));
               } else if (response.statusCode() == 406) {
                  return null;
               } else {
                  throw RestClient.unexpected(response);
               }
            }
      );
   }

   @Override
   public Benchmark get() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/benchmark/" + encode(name))
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/java-serialized-object")
                  .send(handler), 200,
            response -> {
               try {
                  return Util.deserialize(response.bodyAsBuffer().getBytes());
               } catch (IOException | ClassNotFoundException e) {
                  throw new CompletionException(e);
               }
            });
   }

   @Override
   public Client.RunRef start(String description) {
      CompletableFuture<Client.RunRef> future = new CompletableFuture<>();
      client.vertx.runOnContext(ctx -> {
         String query = "/benchmark/" + encode(name) + "/start";
         if (description != null) {
            try {
               query += "?desc=" + URLEncoder.encode(description, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
            }
         }
         client.request(HttpMethod.GET, query).send(rsp -> {
            if (rsp.succeeded()) {
               HttpResponse<Buffer> response = rsp.result();
               String location = response.getHeader(HttpHeaders.LOCATION.toString());
               if (response.statusCode() == 202) {
                  if (location == null) {
                     future.completeExceptionally(new RestClientException("Server did not respond with run location!"));
                  }
                  future.complete(new RunRefImpl(client, location));
               } else if (response.statusCode() == 301) {
                  if (location == null) {
                     future.completeExceptionally(new RestClientException("Server did not respond with run location!"));
                  }
                  URL url;
                  try {
                     url = new URL(location);
                  } catch (MalformedURLException e) {
                     future.completeExceptionally(new RestClientException("Cannot parse URL " + location, new RestClientException(e)));
                     return;
                  }
                  String runId = response.getHeader("x-run-id");
                  client.request(HttpMethod.GET, url.getPort(), url.getHost(), url.getFile()).send(rsp2 -> {
                     if (rsp2.succeeded()) {
                        HttpResponse<Buffer> response2 = rsp2.result();
                        if (response2.statusCode() >= 200 && response2.statusCode() < 300) {
                           future.complete(new RunRefImpl(client, runId == null ? "last" : runId));
                        } else {
                           future.completeExceptionally(new RestClientException("Failed to indirectly trigger job on " + location + ", status is " + response2.statusCode()));
                        }
                     } else {
                        future.completeExceptionally(new RestClientException("Failed to indirectly trigger job on " + location, rsp2.cause()));
                     }
                  });
               } else {
                  future.completeExceptionally(RestClient.unexpected(response));
               }
            } else {
               future.completeExceptionally(rsp.cause());
            }
         });
      });
      return RestClient.waitFor(future);
   }

   @Override
   public String structure() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/benchmark/" + encode(name) + "/structure")
                  .putHeader(HttpHeaders.ACCEPT.toString(), "text/vnd.yaml")
                  .send(handler), 200,
            HttpResponse::bodyAsString);
   }

   private String encode(String name) {
      try {
         return URLEncoder.encode(name, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         return name;
      }
   }
}
