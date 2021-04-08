package io.hyperfoil.client;

import static io.hyperfoil.client.RestClient.unexpected;
import static io.hyperfoil.client.RestClient.waitFor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.Run;
import io.hyperfoil.core.util.Util;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.ext.web.client.HttpResponse;

public class RunRefImpl implements Client.RunRef {
   private final RestClient client;
   private final String id;

   public RunRefImpl(RestClient client, String id) {
      this.client = client;
      // Accepting URL as id
      int lastSlash = id.lastIndexOf('/');
      this.id = lastSlash >= 0 ? id.substring(lastSlash + 1) : id;
   }

   @Override
   public String id() {
      return id;
   }

   @Override
   public Run get() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id).send(handler), 200,
            response -> Json.decodeValue(response.body(), Run.class));
   }

   @Override
   public Client.RunRef kill() {
      client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/kill").send(handler), 202,
            response -> null);
      return this;
   }

   @Override
   public Benchmark benchmark() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/benchmark")
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
   public Map<String, Map<String, Client.MinMax>> sessionStatsRecent() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/sessions/recent").send(handler), 200,
            response -> JacksonCodec.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {})
      );
   }

   @Override
   public Map<String, Map<String, Client.MinMax>> sessionStatsTotal() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/sessions/total").send(handler), 200,
            response -> JacksonCodec.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {})
      );
   }

   @Override
   public Collection<String> sessions() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/sessions").send(handler), 200,
            response -> Arrays.asList(response.bodyAsString().split("\n")));
   }

   @Override
   public Collection<String> connections() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/connections").send(handler), 200,
            response -> Arrays.asList(response.bodyAsString().split("\n")));

   }

   @Override
   public Map<String, Map<String, Client.MinMax>> connectionStatsRecent() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/connections/recent").send(handler), 200,
            response -> JacksonCodec.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {}));
   }

   @Override
   public Map<String, Map<String, Client.MinMax>> connectionStatsTotal() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/connections/total").send(handler), 200,
            response -> JacksonCodec.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {}));
   }

   @Override
   public RequestStatisticsResponse statsRecent() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/stats/recent")
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/json").send(handler), 200,
            response -> Json.decodeValue(response.body(), RequestStatisticsResponse.class));
   }

   @Override
   public RequestStatisticsResponse statsTotal() {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/stats/total")
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/json").send(handler), 200,
            response -> Json.decodeValue(response.body(), RequestStatisticsResponse.class));
   }

   @Override
   public byte[] statsAll(String format) {
      CompletableFuture<byte[]> future = new CompletableFuture<>();
      client.vertx.runOnContext(ctx ->
         client.request(HttpMethod.GET, "/run/" + id + "/stats/all")
               .putHeader(HttpHeaders.ACCEPT.toString(), format)
               .send(rsp -> {
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
                     future.complete(response.body().getBytes());
                  } catch (Throwable t) {
                     future.completeExceptionally(t);
                  }
               }));
      return waitFor(future);
   }

   @Override
   public Histogram histogram(String phase, int stepId, String metric) {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/stats/histogram")
                  .addQueryParam("phase", phase)
                  .addQueryParam("stepId", String.valueOf(stepId))
                  .addQueryParam("metric", metric)
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/json").send(handler), 200,
            response -> Json.decodeValue(response.body(), Histogram.class)
      );
   }

   @Override
   public byte[] file(String filename) {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/file").addQueryParam("file", filename).send(handler), 200,
            response -> response.body().getBytes()
      );
   }

   @Override
   public byte[] report(String source) {
      return client.sync(
            handler -> client.request(HttpMethod.GET, "/run/" + id + "/report").send(handler), 200,
            response -> response.body().getBytes()
      );
   }
}
