package io.hyperfoil.client;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Version;
import io.hyperfoil.util.Util;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class RestClient implements Client, Closeable {
   final Vertx vertx = Vertx.vertx();
   final WebClientOptions options;
   final WebClient client;

   public RestClient(String host, int port) {
      // Actually there's little point in using async client, but let's stay in Vert.x libs
      options = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);
      client = WebClient.create(vertx, options.setFollowRedirects(false));
   }

   static RestClientException unexpected(HttpResponse<Buffer> response) {
      StringBuilder sb = new StringBuilder("Server responded with unexpected code: ");
      sb.append(response.statusCode()).append(", ").append(response.statusMessage());
      String body = response.bodyAsString();
      if (body != null && !body.isEmpty()) {
         sb.append(":\n").append(body);
      }
      return new RestClientException(sb.toString());
   }

   public String host() {
      return options.getDefaultHost();
   }

   public int port() {
      return options.getDefaultPort();
   }


   @Override
   public BenchmarkRef register(Benchmark benchmark) {
      byte[] bytes;
      try {
         bytes = Util.serialize(benchmark);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
      return sync(
            handler -> client.request(HttpMethod.POST, "/benchmark")
                  .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/java-serialized-object")
                  .sendBuffer(Buffer.buffer(bytes), handler), 204,
            response -> new BenchmarkRefImpl(this, benchmark.name()));
   }

   @Override
   public List<String> benchmarks() {
      return sync(
            handler -> client.request(HttpMethod.GET, "/benchmark").send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class)));
   }

   @Override
   public BenchmarkRef benchmark(String name) {
      return new BenchmarkRefImpl(this, name);
   }

   @Override
   public List<io.hyperfoil.controller.model.Run> runs(boolean details) {
      return sync(
            handler -> client.request(HttpMethod.GET, "/run?details=" + details).send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), io.hyperfoil.controller.model.Run[].class)));
   }

   @Override
   public RunRef run(String id) {
      return new RunRefImpl(this, id);
   }

   @Override
   public long ping() {
      return sync(handler -> client.request(HttpMethod.GET, "/").send(handler), 200, response -> {
         try {
            String header = response.getHeader("x-epoch-millis");
            return header != null ? Long.parseLong(header) : 0L;
         } catch (NumberFormatException e) {
            return 0L;
         }
      });
   }

   @Override
   public Version version() {
      return sync(handler -> client.request(HttpMethod.GET, "/version").send(handler), 200,
            response -> Json.decodeValue(response.body(), Version.class));
   }

   @Override
   public Collection<String> agents() {
      return sync(handler -> client.request(HttpMethod.GET, "/agents").send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class)));
   }

   @Override
   public String downloadLog(String node, String logId, long offset, String destinationFile) {
      String url = "/log" + (node == null ? "" : "/" + node);
      // When there's no more data, content-length won't be present and the body is null
      // the etag does not match
      CompletableFuture<String> future = new CompletableFuture<>();
      vertx.runOnContext(ctx -> {
         client.request(HttpMethod.GET, url + "?offset=" + offset).send(rsp -> {
            if (rsp.failed()) {
               future.completeExceptionally(rsp.cause());
               return;
            }
            HttpResponse<Buffer> response = rsp.result();
            if (response.statusCode() != 200) {
               future.completeExceptionally(unexpected(response));
               return;
            }
            try {
               String etag = response.getHeader(HttpHeaders.ETAG.toString());
               if (logId == null) {
                  try {
                     Files.write(Paths.get(destinationFile), response.body().getBytes());
                  } catch (IOException e) {
                     throw new RestClientException(e);
                  }
                  future.complete(etag);
               } else if (etag != null && etag.equals(logId)) {
                  if (response.body() != null) {
                     // When there's no more data, content-length won't be present and the body is null
                     try (RandomAccessFile rw = new RandomAccessFile(destinationFile, "rw")) {
                        rw.seek(offset);
                        rw.write(response.body().getBytes());
                     } catch (IOException e) {
                        throw new RestClientException(e);
                     }
                  }
                  future.complete(etag);
               } else {
                  // the etag does not match
                  client.request(HttpMethod.GET, url).send(rsp2 -> {
                     if (rsp2.failed()) {
                        future.completeExceptionally(rsp2.cause());
                        return;
                     }
                     HttpResponse<Buffer> response2 = rsp2.result();
                     if (response2.statusCode() != 200) {
                        future.completeExceptionally(unexpected(response2));
                        return;
                     }
                     try {
                        Files.write(Paths.get(destinationFile), response2.body().getBytes());
                        future.complete(response2.getHeader(HttpHeaders.ETAG.toString()));
                     } catch (Throwable t) {
                        future.completeExceptionally(t);
                     }
                  });
               }
            } catch (Throwable t) {
               future.completeExceptionally(t);
            }
         });
      });
      return waitFor(future);
   }

   static <T> T waitFor(CompletableFuture<T> future) {
      try {
         return future.get(30, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new RestClientException(e);
      } catch (ExecutionException e) {
         if (e.getCause() instanceof RestClientException) {
            throw (RestClientException) e.getCause();
         }
         throw new RestClientException(e.getCause() == null ? e : e.getCause());
      } catch (TimeoutException e) {
         throw new RestClientException("Request did not complete within 30 seconds.");
      }
   }

   <T> T sync(Consumer<Handler<AsyncResult<HttpResponse<Buffer>>>> invoker, int statusCode, Function<HttpResponse<Buffer>, T> f) {
      CompletableFuture<T> future = new CompletableFuture<>();
      vertx.runOnContext(ctx -> {
         invoker.accept(rsp -> {
            if (rsp.succeeded()) {
               HttpResponse<Buffer> response = rsp.result();
               if (statusCode != 0 && response.statusCode() != statusCode) {
                  future.completeExceptionally(unexpected(response));
                  return;
               }
               try {
                  future.complete(f.apply(response));
               } catch (Throwable t) {
                  future.completeExceptionally(t);
               }
            } else {
               future.completeExceptionally(rsp.cause());
            }
         });
      });
      return waitFor(future);
   }

   @Override
   public void close() {
      client.close();
      vertx.close();
   }

   public String toString() {
      return options.getDefaultHost() + ":" + options.getDefaultPort();
   }
}
