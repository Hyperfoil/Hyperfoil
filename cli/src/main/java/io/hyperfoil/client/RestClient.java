package io.hyperfoil.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.Benchmark;
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
   private final Vertx vertx = Vertx.vertx();
   final WebClientOptions options;
   final WebClient client;

   public RestClient(String host, int port) {
      // Actually there's little point in using async client, but let's stay in Vert.x libs
      options = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);
      client = WebClient.create(vertx, options.setFollowRedirects(false));
   }

   static void expectStatus(HttpResponse<Buffer> response, int statusCode) {
      if (response.statusCode() != statusCode) {
         throw new RestClientException("Server responded with unexpected code: " + response.statusCode() + ", " + response.statusMessage());
      }
   }

   public String host() {
      return options.getDefaultHost();
   }

   public int port() {
      return options.getDefaultPort();
   }

   @Override
   public Collection<Agent> agents() {
      return sync(
            handler -> client.request(HttpMethod.GET, "/agents").send(handler),
            response -> Arrays.asList(Json.decodeValue(response.body(), Agent[].class)));
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
                  .sendBuffer(Buffer.buffer(bytes), handler),
            response -> {
               expectStatus(response, 204);
               return new BenchmarkRefImpl(this, benchmark.name());
            }
      );
   }

   @Override
   public Collection<String> benchmarks() {
      return sync(
            handler -> client.request(HttpMethod.GET, "/benchmark").send(handler),
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class))
      );
   }

   @Override
   public BenchmarkRef benchmark(String name) {
      return new BenchmarkRefImpl(this, name);
   }

   @Override
   public Collection<String> runs() {
      return sync(
            handler -> client.request(HttpMethod.GET, "/run").send(handler),
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class))
      );
   }

   @Override
   public RunRef run(String id) {
      return new RunRefImpl(this, id);
   }

   <T> T sync(Consumer<Handler<AsyncResult<HttpResponse<Buffer>>>> invoker, Function<HttpResponse<Buffer>, T> f) {
      CompletableFuture<T> future = new CompletableFuture<>();
      vertx.runOnContext(ctx -> {
         invoker.accept(rsp -> {
            if (rsp.succeeded()) {
               try {
                  future.complete(f.apply(rsp.result()));
               } catch (Throwable t) {
                  future.completeExceptionally(t);
               }
            } else {
               future.completeExceptionally(rsp.cause());
            }
         });
      });
      return future.join();
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
