package io.hyperfoil.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.controller.Client;
import io.hyperfoil.impl.Util;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpRequest;
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
                  return new Client.BenchmarkSource(response.bodyAsString(), response.getHeader(HttpHeaders.ETAG.toString()), response.headers().getAll("x-file"));
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
   public Client.RunRef start(String description, Map<String, String> templateParams) {
      CompletableFuture<Client.RunRef> future = new CompletableFuture<>();
      client.vertx.runOnContext(ctx -> {
         HttpRequest<Buffer> request = client.request(HttpMethod.GET, "/benchmark/" + encode(name) + "/start");
         if (description != null) {
            request.addQueryParam("desc", description);
         }
         for (var param : templateParams.entrySet()) {
            request.addQueryParam("templateParam", param.getKey() + "=" + param.getValue());
         }
         request.send(rsp -> {
            if (rsp.succeeded()) {
               HttpResponse<Buffer> response = rsp.result();
               String location = response.getHeader(HttpHeaders.LOCATION.toString());
               if (response.statusCode() == 202) {
                  if (location == null) {
                     future.completeExceptionally(new RestClientException("Server did not respond with run location!"));
                  } else {
                     future.complete(new RunRefImpl(client, location));
                  }
               } else if (response.statusCode() == 301) {
                  if (location == null) {
                     future.completeExceptionally(new RestClientException("Server did not respond with run location!"));
                     return;
                  }
                  URL url;
                  try {
                     url = new URL(location);
                  } catch (MalformedURLException e) {
                     future.completeExceptionally(new RestClientException("Cannot parse URL " + location, new RestClientException(e)));
                     return;
                  }
                  String runId = response.getHeader("x-run-id");
                  client.request(HttpMethod.GET, "https".equalsIgnoreCase(url.getProtocol()), url.getHost(), url.getPort(), url.getFile()).send(rsp2 -> {
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
   public Client.BenchmarkStructure structure(Integer maxCollectionSize, Map<String, String> templateParams) {
      return client.sync(
            handler -> {
               HttpRequest<Buffer> request = client.request(HttpMethod.GET, "/benchmark/" + encode(name) + "/structure");
               if (maxCollectionSize != null) {
                  request.addQueryParam("maxCollectionSize", maxCollectionSize.toString());
               }
               for (var param : templateParams.entrySet()) {
                  request.addQueryParam("templateParam", param.getKey() + "=" + param.getValue());
               }
               request
                     .putHeader(HttpHeaders.ACCEPT.toString(), "application/json")
                     .send(handler);
            }, 200,
            response -> Json.decodeValue(response.body(), Client.BenchmarkStructure.class));
   }

   @Override
   public Map<String, byte[]> files() {
      return client.sync(
            handler -> {
               client.request(HttpMethod.GET, "/benchmark/" + encode(name) + "/files")
                     .putHeader(HttpHeaders.ACCEPT.toString(), "multipart/form-data")
                     .send(handler);
            }, 200,
            response -> {
               String contentType = response.getHeader(HttpHeaders.CONTENT_TYPE.toString());
               if (contentType == null) {
                  throw new RestClientException("Missing response content-type.");
               }
               String[] parts = contentType.split(";");
               if (!"multipart/form-data".equals(parts[0].trim()) || parts.length < 2 ||
                     !parts[1].trim().startsWith("boundary=\"") || !parts[1].trim().endsWith("\"")) {
                  throw new RestClientException("Unexpected content-type: " + contentType);
               }
               String param = parts[1].trim();
               String boundary = param.substring(10, param.length() - 1);
               Map<String, byte[]> files = new HashMap<>();
               try (ByteArrayInputStream stream = new ByteArrayInputStream(response.bodyAsBuffer().getBytes())) {
                  int length = -1;
                  String filename = null;
                  byte[] buffer = new byte[2048];
                  for (; ; ) {
                     int b, pos = 0;
                     while (pos < buffer.length && (b = stream.read()) >= 0 && b != '\n') {
                        buffer[pos++] = (byte) b;
                     }
                     if (pos == buffer.length) {
                        throw new RestClientException("Too long line; probably protocol error.");
                     }
                     String line = new String(buffer, 0, pos, StandardCharsets.US_ASCII);
                     String lower = line.toLowerCase(Locale.ENGLISH);
                     if (line.startsWith("--" + boundary)) {
                        if (line.endsWith("--")) {
                           break;
                        }
                     } else if (lower.startsWith("content-type: ")) {
                        // ignore
                     } else if (lower.startsWith("content-length: ")) {
                        try {
                           length = Integer.parseInt(lower.substring("content-length: ".length()).trim());
                        } catch (NumberFormatException e) {
                           throw new RestClientException("Cannot parse content-length: " + line);
                        }
                     } else if (lower.startsWith("content-disposition: ")) {
                        String[] disposition = line.substring("content-disposition: ".length()).split(";");
                        for (int i = 0; i < disposition.length; ++i) {
                           String d = disposition[i].trim();
                           if (d.startsWith("filename=\"") && d.endsWith("\"")) {
                              filename = d.substring(10, d.length() - 1);
                           }
                        }
                     } else if (line.isEmpty()) {
                        if (length < 0) {
                           throw new RestClientException("Missing content-length!");
                        } else {
                           byte[] bytes = new byte[length];
                           if (stream.readNBytes(bytes, 0, length) != length) {
                              throw new RestClientException("Cannot read all bytes for file " + filename);
                           }
                           if (filename == null) {
                              throw new RestClientException("No filename in content-disposition");
                           }
                           files.put(filename, bytes);
                           if (stream.read() != '\n') {
                              throw new RestClientException("Expected newline after file " + filename);
                           }
                           filename = null;
                           length = -1;
                        }
                     }
                  }
               } catch (IOException e) {
                  throw new RestClientException(e);
               }
               return files;
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
