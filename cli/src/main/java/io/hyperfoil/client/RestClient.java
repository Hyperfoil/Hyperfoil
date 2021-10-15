package io.hyperfoil.client;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatsExtension;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.model.Version;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.internal.Properties;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.multipart.MultipartForm;

public class RestClient implements Client, Closeable {
   private static final long REQUEST_TIMEOUT = Properties.getLong(Properties.CLI_REQUEST_TIMEOUT, 30000);

   final Vertx vertx;
   final WebClientOptions options;
   private final WebClient client;
   private String authorization;

   static {
      StatsExtension.registerSubtypes();
   }

   public RestClient(Vertx vertx, String host, int port, boolean ssl, boolean insecure, String password) {
      this.vertx = vertx;
      // Actually there's little point in using async client, but let's stay in Vert.x libs
      options = new WebClientOptions().setDefaultHost(host).setDefaultPort(port);
      if (ssl) {
         options.setSsl(true).setUseAlpn(true);
      }
      if (insecure) {
         options.setTrustAll(true).setVerifyHost(false);
      }
      client = WebClient.create(this.vertx, options.setFollowRedirects(false));
      setPassword(password);
   }

   public void setPassword(String password) {
      if (password != null) {
         // server ignores username
         authorization = "Basic " + Base64.getEncoder().encodeToString(("hyperfoil:" + password).getBytes(StandardCharsets.UTF_8));
      } else {
         authorization = null;
      }
   }

   public void setToken(String token) {
      if (token != null) {
         authorization = "Bearer " + token;
      } else {
         authorization = null;
      }
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

   HttpRequest<Buffer> request(HttpMethod method, String path) {
      HttpRequest<Buffer> request = client.request(method, path);
      if (authorization != null) {
         request.putHeader(HttpHeaders.AUTHORIZATION.toString(), authorization);
      }
      return request;
   }

   HttpRequest<Buffer> request(HttpMethod method, boolean ssl, String host, int port, String path) {
      return client.request(method, new RequestOptions().setSsl(ssl).setHost(host).setPort(port).setURI(path));
   }

   @Override
   public BenchmarkRef register(Benchmark benchmark, String prevVersion) {
      byte[] bytes;
      try {
         bytes = Util.serialize(benchmark);
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
      return sync(
            handler -> {
               HttpRequest<Buffer> request = request(HttpMethod.POST, "/benchmark");
               if (prevVersion != null) {
                  request.putHeader(HttpHeaders.IF_MATCH.toString(), prevVersion);
               }
               request.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/java-serialized-object")
                     .sendBuffer(Buffer.buffer(bytes), handler);
            }, 0,
            response -> {
               if (response.statusCode() == 204) {
                  return new BenchmarkRefImpl(this, benchmark.name());
               } else if (response.statusCode() == 409) {
                  throw new EditConflictException();
               } else {
                  throw unexpected(response);
               }
            });
   }

   @Override
   public BenchmarkRef register(String benchmarkFile, Map<String, Path> otherFiles, String prevVersion, String storedFilesBenchmark) {
      return sync(
            handler -> {
               MultipartForm multipart = MultipartForm.create()
                     .textFileUpload("benchmark", "benchmark.yaml", benchmarkFile, "text/vnd.yaml");
               for (Map.Entry<String, Path> entry : otherFiles.entrySet()) {
                  multipart.binaryFileUpload(entry.getKey(), entry.getKey(), entry.getValue().toString(), "application/octet-stream");
               }
               HttpRequest<Buffer> request = request(HttpMethod.POST, "/benchmark");
               if (storedFilesBenchmark != null) {
                  request.addQueryParam("storedFilesBenchmark", storedFilesBenchmark);
               }
               if (prevVersion != null) {
                  request.putHeader(HttpHeaders.IF_MATCH.toString(), prevVersion);
               }
               request.sendMultipartForm(multipart, handler);
            }, 0,
              this::processRegisterResponse);
   }

   @Override
   public BenchmarkRef registerLocal(String benchmarkUri, String prevVersion, String storedFilesBenchmark) {
      return sync(
              handler -> {
                 HttpRequest<Buffer> request = request(HttpMethod.POST, "/benchmark");
                 if (prevVersion != null) {
                    request.putHeader(HttpHeaders.IF_MATCH.toString(), prevVersion);
                 }
                 request.putHeader(HttpHeaders.CONTENT_TYPE.toString(), "text/uri-list")
                         .sendBuffer(Buffer.buffer(benchmarkUri), handler);
              }, 0,
              this::processRegisterResponse);
   }

   private BenchmarkRefImpl processRegisterResponse(HttpResponse<Buffer> response) {
      if (response.statusCode() == 204) {
         String location = response.getHeader(HttpHeaders.LOCATION.toString());
         if (location == null) {
            throw new RestClientException("Expected location header.");
         }
         int lastSlash = location.lastIndexOf('/');
         return new BenchmarkRefImpl(this, location.substring(lastSlash + 1));
      } else if (response.statusCode() == 409) {
         throw new EditConflictException();
      } else {
         throw unexpected(response);
      }
   }

   @Override
   public List<String> benchmarks() {
      return sync(
            handler -> request(HttpMethod.GET, "/benchmark").send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class)));
   }

   @Override
   public BenchmarkRef benchmark(String name) {
      return new BenchmarkRefImpl(this, name);
   }

   @Override
   public List<io.hyperfoil.controller.model.Run> runs(boolean details) {
      return sync(
            handler -> request(HttpMethod.GET, "/run?details=" + details).send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), io.hyperfoil.controller.model.Run[].class)));
   }

   @Override
   public RunRef run(String id) {
      return new RunRefImpl(this, id);
   }

   @Override
   public long ping() {
      return sync(handler -> request(HttpMethod.GET, "/").send(handler), 200, response -> {
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
      return sync(handler -> request(HttpMethod.GET, "/version").send(handler), 0,
            response -> {
               if (response.statusCode() == 401) {
                  throw new Unauthorized();
               } else if (response.statusCode() == 403) {
                  throw new Forbidden();
               } else if (response.statusCode() >= 300 && response.statusCode() <= 399) {
                  String location = response.getHeader(HttpHeaders.LOCATION.toString());
                  if (location == null) {
                     throw new RestClientException("Servers suggests redirection but does not include the Location header");
                  }
                  int pathIndex = location.indexOf('/', 8); // 8 should work for both http and https
                  if (pathIndex >= 0) {
                     location = location.substring(0, pathIndex);
                  }
                  throw new RedirectToHost(location);
               } else if (response.statusCode() != 200) {
                  throw unexpected(response);
               }
               return Json.decodeValue(response.body(), Version.class);
            });
   }

   @Override
   public Collection<String> agents() {
      return sync(handler -> request(HttpMethod.GET, "/agents").send(handler), 200,
            response -> Arrays.asList(Json.decodeValue(response.body(), String[].class)));
   }

   @Override
   public String downloadLog(String node, String logId, long offset, File destinationFile) {
      String url = "/log" + (node == null ? "" : "/" + node);
      // When there's no more data, content-length won't be present and the body is null
      // the etag does not match
      CompletableFuture<String> future = new CompletableFuture<>();
      vertx.runOnContext(ctx -> {
         HttpRequest<Buffer> request = request(HttpMethod.GET, url + "?offset=" + offset);
         if (logId != null) {
            request.putHeader(HttpHeaders.IF_MATCH.toString(), logId);
         }
         request.send(rsp -> {
            if (rsp.failed()) {
               future.completeExceptionally(rsp.cause());
               return;
            }
            HttpResponse<Buffer> response = rsp.result();
            if (response.statusCode() == 412) {
               downloadFullLog(destinationFile, url, future);
               return;
            } else if (response.statusCode() != 200) {
               future.completeExceptionally(unexpected(response));
               return;
            }
            try {
               String etag = response.getHeader(HttpHeaders.ETAG.toString());
               if (logId == null) {
                  try {
                     byte[] bytes;
                     if (response.body() == null) {
                        bytes = "<empty log file>".getBytes(StandardCharsets.UTF_8);
                     } else {
                        bytes = response.body().getBytes();
                     }
                     Files.write(destinationFile.toPath(), bytes);
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
                  downloadFullLog(destinationFile, url, future);
               }
            } catch (Throwable t) {
               future.completeExceptionally(t);
            }
         });
      });
      return waitFor(future);
   }

   @Override
   public void shutdown(boolean force) {
      sync(handler -> request(HttpMethod.GET, "/shutdown?force=" + force).send(handler), 200, response -> null);
   }

   private void downloadFullLog(File destinationFile, String url, CompletableFuture<String> future) {
      // the etag does not match
      request(HttpMethod.GET, url).send(rsp -> {
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
            Files.write(destinationFile.toPath(), response.body().getBytes());
            future.complete(response.getHeader(HttpHeaders.ETAG.toString()));
         } catch (Throwable t) {
            future.completeExceptionally(t);
         }
      });
   }

   static <T> T waitFor(CompletableFuture<T> future) {
      try {
         return future.get(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new RestClientException(e);
      } catch (ExecutionException e) {
         if (e.getCause() instanceof RestClientException) {
            throw (RestClientException) e.getCause();
         }
         throw new RestClientException(e.getCause() == null ? e : e.getCause());
      } catch (TimeoutException e) {
         throw new RestClientException("Request did not complete within " + REQUEST_TIMEOUT + " ms");
      }
   }

   <T> T sync(Consumer<Handler<AsyncResult<HttpResponse<Buffer>>>> invoker, int statusCode, Function<HttpResponse<Buffer>, T> f) {
      CompletableFuture<T> future = new CompletableFuture<>();
      vertx.runOnContext(ctx -> {
         try {
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
         } catch (Throwable t) {
            future.completeExceptionally(t);
         }
      });
      return waitFor(future);
   }

   @Override
   public void close() {
      client.close();
   }

   public String toString() {
      return options.getDefaultHost() + ":" + options.getDefaultPort();
   }

   public static class Unauthorized extends RestClientException {
      public Unauthorized() {
         super("Unauthorized: password required");
      }
   }

   public static class Forbidden extends RestClientException {
      public Forbidden() {
         super("Forbidden: password incorrect");
      }
   }

   public static class RedirectToHost extends RestClientException {
      public String host;

      public RedirectToHost(String host) {
         super("Required redirect");
         this.host = host;
      }
   }
}
