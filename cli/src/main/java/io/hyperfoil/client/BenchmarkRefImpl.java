package io.hyperfoil.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.util.Util;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

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
   public Benchmark get() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/benchmark/" + encode(name))
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
   public Client.RunRef start() {
      return client.sync(
            handler -> client.client.request(HttpMethod.GET, "/benchmark/" + encode(name) + "/start").send(handler), 202,
            response -> {
               String location = response.getHeader(HttpHeaders.LOCATION.toString());
               if (location == null) {
                  throw new RestClientException("Server did not respond with run location!");
               }
               return new RunRefImpl(client, location);
            });
   }

   private String encode(String name) {
      try {
         return URLEncoder.encode(name, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         return name;
      }
   }
}
