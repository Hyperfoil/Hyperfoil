package io.hyperfoil.client;

import java.util.Arrays;
import java.util.Collection;

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
   public String statsRecent() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/stats/recent").send(handler), 200,
            response -> response.bodyAsString());
   }

   @Override
   public String statsTotal() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/run/" + id + "/stats/total").send(handler), 200,
            response -> response.bodyAsString());
   }
}
