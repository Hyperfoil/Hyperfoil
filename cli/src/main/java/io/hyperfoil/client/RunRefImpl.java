package io.hyperfoil.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionException;

import com.fasterxml.jackson.core.type.TypeReference;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.util.Util;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;

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
   public Client.Run get() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id).send(handler), 200,
            response -> Json.decodeValue(response.body(), Client.Run.class));
   }

   @Override
   public Client.RunRef kill() {
      client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/kill").send(handler), 202,
            response -> null);
      return this;
   }

   @Override
   public Benchmark benchmark() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/benchmark")
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
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/sessions/recent").send(handler), 200,
            response -> Json.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {})
      );
   }

   @Override
   public Map<String, Map<String, Client.MinMax>> sessionStatsTotal() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/sessions/total").send(handler), 200,
            response -> Json.decodeValue(response.body(), new TypeReference<Map<String, Map<String, Client.MinMax>>>() {})
      );
   }

   @Override
   public Collection<String> sessions() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/sessions").send(handler), 200,
            response -> Arrays.asList(response.bodyAsString().split("\n")));
   }

   @Override
   public Collection<String> connections() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/connections").send(handler), 200,
            response -> Arrays.asList(response.bodyAsString().split("\n")));

   }

   @Override
   public Client.RequestStatisticsResponse statsRecent() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/stats/recent")
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/json").send(handler), 200,
            response -> Json.decodeValue(response.body(), Client.RequestStatisticsResponse.class));
   }

   @Override
   public Client.RequestStatisticsResponse statsTotal() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/stats/total")
                  .putHeader(HttpHeaders.ACCEPT.toString(), "application/json").send(handler), 200,
            response -> Json.decodeValue(response.body(), Client.RequestStatisticsResponse.class));
   }

   @Override
   public Collection<Client.CustomStats> customStats() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/stats/custom").send(handler), 200,
            response -> Json.decodeValue(response.body(), new TypeReference<Collection<Client.CustomStats>>() {}));
   }
}
