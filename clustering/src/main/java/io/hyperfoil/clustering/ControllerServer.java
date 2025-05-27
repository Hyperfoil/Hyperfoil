package io.hyperfoil.clustering;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.commons.util.FileLookupFactory;

import io.hyperfoil.api.Version;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.clustering.util.PersistedBenchmarkData;
import io.hyperfoil.clustering.webcli.WebCLI;
import io.hyperfoil.controller.ApiService;
import io.hyperfoil.controller.Client;
import io.hyperfoil.controller.StatisticsStore;
import io.hyperfoil.controller.model.Histogram;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.controller.router.ApiRouter;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.print.YamlVisitor;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.util.LowHigh;
import io.hyperfoil.impl.Util;
import io.hyperfoil.internal.Controller;
import io.hyperfoil.internal.Properties;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FaviconHandler;
import io.vertx.ext.web.handler.StaticHandler;

class ControllerServer implements ApiService {
   private static final Logger log = LogManager.getLogger(ControllerServer.class);

   private static final String MIME_TYPE_JSON = "application/json";
   private static final String MIME_TYPE_SERIALIZED = "application/java-serialized-object";
   private static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
   private static final String MIME_TYPE_YAML = "text/vnd.yaml";

   private static final String KEYSTORE_PATH = Properties.get(Properties.CONTROLLER_KEYSTORE_PATH, null);
   private static final String KEYSTORE_PASSWORD = Properties.get(Properties.CONTROLLER_KEYSTORE_PASSWORD, null);
   private static final String PEM_KEYS = Properties.get(Properties.CONTROLLER_PEM_KEYS, null);
   private static final String PEM_CERTS = Properties.get(Properties.CONTROLLER_PEM_CERTS, null);
   private static final String CONTROLLER_PASSWORD = Properties.get(Properties.CONTROLLER_PASSWORD, null);
   private static final boolean CONTROLLER_SECURED_VIA_PROXY = Properties.getBoolean(Properties.CONTROLLER_SECURED_VIA_PROXY);
   private static final String CONTROLLER_EXTERNAL_URI = Properties.get(Properties.CONTROLLER_EXTERNAL_URI, null);
   private static final String TRIGGER_URL = Properties.get(Properties.TRIGGER_URL, null);

   private static final String BEARER_TOKEN;

   private static final Comparator<ControllerPhase> PHASE_COMPARATOR = Comparator
         .<ControllerPhase, Long> comparing(ControllerPhase::absoluteStartTime).thenComparing(p -> p.definition().name);
   private static final BinaryOperator<Run> LAST_RUN_OPERATOR = (r1, r2) -> r1.id.compareTo(r2.id) > 0 ? r1 : r2;
   private static final String DATAKEY = "[/**DATAKEY**/]";

   static {
      byte[] token = new byte[48];
      new SecureRandom().nextBytes(token);
      BEARER_TOKEN = Base64.getEncoder().encodeToString(token);
   }

   final ControllerVerticle controller;
   HttpServer httpServer;
   String baseURL;

   ControllerServer(ControllerVerticle controller, CountDown countDown) {
      this.controller = controller;

      HttpServerOptions options = new HttpServerOptions();
      if (KEYSTORE_PATH != null) {
         options.setSsl(true).setUseAlpn(true).setKeyCertOptions(
               new JksOptions().setPath(KEYSTORE_PATH).setPassword(KEYSTORE_PASSWORD));
      } else if (PEM_CERTS != null || PEM_KEYS != null) {
         PemKeyCertOptions pem = new PemKeyCertOptions();
         if (PEM_CERTS != null) {
            for (String certPath : PEM_CERTS.split(",")) {
               pem.addCertPath(certPath.trim());
            }
         }
         if (PEM_KEYS != null) {
            for (String keyPath : PEM_KEYS.split(",")) {
               pem.addKeyPath(keyPath.trim());
            }
         }
         options.setSsl(true).setUseAlpn(true).setKeyCertOptions(pem);
      }

      Router router = Router.router(controller.getVertx());
      if (CONTROLLER_PASSWORD != null) {
         if (!options.isSsl() && !CONTROLLER_SECURED_VIA_PROXY) {
            throw new IllegalStateException("Server uses basic authentication scheme (" + Properties.CONTROLLER_PASSWORD +
                  " is set) but it does not use TLS connections. If the confidentiality is guaranteed by a proxy set -D" +
                  Properties.CONTROLLER_SECURED_VIA_PROXY + "=true.");
         }
         log.info("Server is protected using a password.");
         router.route().handler(new BasicAuthHandler());
      }
      StaticHandler staticHandler = StaticHandler.create().setCachingEnabled(true);
      router.route("/").handler(staticHandler);
      router.route("/web/*").handler(staticHandler);
      router.route("/favicon.ico").handler(FaviconHandler.create(controller.getVertx(), "webroot/favicon.ico"));
      new ApiRouter(this, router);

      String controllerHost = Properties.get(Properties.CONTROLLER_HOST,
            controller.getConfig().getString(Properties.CONTROLLER_HOST, "0.0.0.0"));
      int controllerPort = Properties.getInt(Properties.CONTROLLER_PORT,
            controller.getConfig().getInteger(Properties.CONTROLLER_PORT, 8090));
      WebCLI webCLI = new WebCLI(controller.getVertx());
      httpServer = controller.getVertx().createHttpServer(options).requestHandler(router)
            .webSocketHandler(webCLI)
            .listen(controllerPort, controllerHost, serverResult -> {
               if (serverResult.succeeded()) {
                  String host = controllerHost;
                  // Can't advertise 0.0.0.0 as
                  if (host.equals("0.0.0.0")) {
                     try {
                        host = InetAddress.getLocalHost().getHostName();
                     } catch (UnknownHostException e) {
                        host = "localhost";
                     }
                  }
                  if (CONTROLLER_EXTERNAL_URI == null) {
                     baseURL = (options.isSsl() ? "https://" : "http://") + host + ":" + serverResult.result().actualPort();
                  } else {
                     baseURL = CONTROLLER_EXTERNAL_URI;
                  }
                  webCLI.setConnectionOptions(host, serverResult.result().actualPort(), options.isSsl());
                  log.info("Hyperfoil controller listening on {}", baseURL);
               }
               countDown.handle(serverResult.mapEmpty());
            });
   }

   void stop(Promise<Void> stopFuture) {
      httpServer.close(result -> stopFuture.complete());
   }

   @Override
   public void openApi(RoutingContext ctx) {
      try {
         InputStream stream = ApiService.class.getClassLoader().getResourceAsStream("openapi.yaml");
         Buffer payload;
         String contentType;
         if (stream == null) {
            payload = Buffer.buffer("API definition not available");
            contentType = MIME_TYPE_TEXT_PLAIN;
         } else {
            payload = Buffer.buffer(Util.toString(stream));
            contentType = MIME_TYPE_YAML;
         }
         ctx.response()
               .putHeader(HttpHeaders.CONTENT_TYPE.toString(), contentType)
               .putHeader("x-epoch-millis", String.valueOf(System.currentTimeMillis()))
               .end(payload);
      } catch (IOException e) {
         log.error("Cannot read OpenAPI definition", e);
         ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
               .setStatusMessage("Cannot read OpenAPI definition.").end();
      }
   }

   private void respondWithJson(RoutingContext ctx, boolean pretty, Object entity) {
      ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON)
            .end(pretty ? Json.encodePrettily(entity) : Json.encode(entity));
   }

   private void respondWithJson(RoutingContext ctx, JsonObject entity) {
      ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON)
            .end(entity.encodePrettily());
   }

   @Override
   public void listBenchmarks(RoutingContext ctx) {
      respondWithJson(ctx, true, controller.getBenchmarks());
   }

   @Override
   public void listTemplates(RoutingContext ctx) {
      respondWithJson(ctx, true, controller.getTemplates());
   }

   @Override
   public void addBenchmark$application_json(RoutingContext ctx, String ifMatch, String storedFilesBenchmark) {
      addBenchmark$text_vnd_yaml(ctx, ifMatch, storedFilesBenchmark);
   }

   private void addBenchmarkAndReply(RoutingContext ctx, String source, BenchmarkData data, String prevVersion)
         throws ParserException {
      if (source == null || data == null) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Cannot read benchmark.");
         return;
      }
      BenchmarkSource benchmarkSource = BenchmarkParser.instance().createSource(source, data);
      if (benchmarkSource.isTemplate()) {
         Future<Void> future = controller.addTemplate(benchmarkSource, prevVersion);
         sendReply(ctx, future, benchmarkSource.name);
      } else {
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(benchmarkSource, Collections.emptyMap());
         addBenchmarkAndReply(ctx, benchmark, prevVersion);
      }
   }

   private void sendReply(RoutingContext ctx, Future<Void> future, String name) {
      String location = baseURL + "/benchmark/" + encode(name);
      future.onSuccess(nil -> {
         ctx.response()
               .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
               .putHeader(HttpHeaders.LOCATION, location).end();
      }).onFailure(throwable -> {
         if (throwable instanceof VersionConflictException) {
            ctx.response().setStatusCode(HttpResponseStatus.CONFLICT.code()).end();
         } else {
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
         }
      });
   }

   private void addBenchmarkAndReply(RoutingContext ctx, Benchmark benchmark, String prevVersion) {
      if (benchmark == null) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Cannot read benchmark.");
         return;
      }
      if (benchmark.agents().length == 0 && controller.getVertx().isClustered()) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .end("Hyperfoil controller is clustered but the benchmark does not define any agents.");
         return;
      } else if (benchmark.agents().length != 0 && !controller.getVertx().isClustered()) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .end("Hyperfoil runs in standalone mode but the benchmark defines agents for clustering");
         return;
      }
      sendReply(ctx, controller.addBenchmark(benchmark, prevVersion), benchmark.name());
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }

   @Override
   public void addBenchmark$text_uri_list(RoutingContext ctx, String ifMatch, String storedFilesBenchmark) {
      var loadDirProperty = Properties.get(Properties.LOAD_DIR, null);
      if (loadDirProperty == null) {
         log.error("Loading controller local benchmarks is not enabled, set the {} property to enable.",
               Properties.LOAD_DIR);
         ctx.response().setStatusCode(HttpResponseStatus.SERVICE_UNAVAILABLE.code())
               .end("Loading controller local benchmarks is not enabled.");
         return;
      }
      var loadDirPath = Paths.get(loadDirProperty).toAbsolutePath();
      String body = ctx.getBodyAsString();
      if (body == null || body.isEmpty()) {
         log.error("Benchmark is empty, load failed.");
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Benchmark is empty.");
         return;
      }
      // text/uri-list ignores
      var uris = body.lines()
            .map(String::trim)
            .filter(Predicate.not(String::isEmpty))
            .filter(Predicate.not(l -> l.startsWith("#")))
            .flatMap(l -> {
               try {
                  return Stream.of(new URI(l));
               } catch (URISyntaxException e) {
                  return Stream.empty();
               }
            })
            .collect(Collectors.toList());
      if (uris.isEmpty()) {
         log.error("No Benchmark URIs specified, load failed.");
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("No Benchmark URIs specified.");
         return;
      }
      if (uris.size() > 1) {
         log.error("Multiple Benchmark URIs specified, load failed.");
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Multiple Benchmark URIs specified.");
         return;
      }
      var uri = uris.get(0);
      if (uri.getScheme() != null && !"file".equals(uri.getScheme())) {
         log.error("Unsupported URI scheme of {} specified, load failed.", uri.getScheme());
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .end(uri.getScheme() + " scheme URIs are not supported.");
         return;
      }
      var localPath = (uri.getScheme() == null ? Paths.get(uri.getPath()) : Paths.get(uri)).toAbsolutePath();
      if (!localPath.startsWith(loadDirPath) || !Files.isRegularFile(localPath)) {
         log.error("Unknown controller local benchmark {}.", localPath);
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Unknown controller local benchmark.");
         return;
      }
      try {
         String source = Files.readString(localPath);
         BenchmarkData data = new LocalBenchmarkData(localPath);
         if (storedFilesBenchmark != null) {
            storedFilesBenchmark = BenchmarkData.sanitize(storedFilesBenchmark);
            data = new PersistedBenchmarkData(Controller.BENCHMARK_DIR.resolve(storedFilesBenchmark + ".data"));
         }
         addBenchmarkAndReply(ctx, source, data, ifMatch);
      } catch (ParserException | BenchmarkDefinitionException | IOException e) {
         respondParsingError(ctx, e);
      }
   }

   @Override
   public void addBenchmark$text_vnd_yaml(RoutingContext ctx, String ifMatch, String storedFilesBenchmark) {
      String source = ctx.getBodyAsString();
      if (source == null || source.isEmpty()) {
         log.error("Benchmark is empty, upload failed.");
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Benchmark is empty.");
         return;
      }
      try {
         BenchmarkData data = BenchmarkData.EMPTY;
         if (storedFilesBenchmark != null) {
            storedFilesBenchmark = BenchmarkData.sanitize(storedFilesBenchmark);
            data = new PersistedBenchmarkData(Controller.BENCHMARK_DIR.resolve(storedFilesBenchmark + ".data"));
         }
         addBenchmarkAndReply(ctx, source, data, ifMatch);
      } catch (ParserException | BenchmarkDefinitionException e) {
         respondParsingError(ctx, e);
      }
   }

   private void respondParsingError(RoutingContext ctx, Exception e) {
      log.error("Failed to read benchmark", e);
      ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
            .end("Cannot read benchmark: " + Util.explainCauses(e));
   }

   @Override
   public void addBenchmark$application_java_serialized_object(RoutingContext ctx, String ifMatch,
         String storedFilesBenchmark) {
      if (storedFilesBenchmark != null) {
         log.warn("Ignoring parameter useStoredData for serialized benchmark upload.");
      }
      byte[] bytes = ctx.getBody().getBytes();
      try {
         Benchmark benchmark = Util.deserialize(bytes);
         addBenchmarkAndReply(ctx, benchmark, ifMatch);
      } catch (IOException | ClassNotFoundException e) {
         log.error("Failed to deserialize", e);
         StringBuilder message = new StringBuilder(
               "Cannot read benchmark - the controller (server) version and CLI version are probably not in sync.\n");
         message.append("This partial stack-track might help you diagnose the problematic part:\n---\n");
         for (StackTraceElement ste : e.getStackTrace()) {
            message.append(ste).append('\n');
            if (ste.getClassName().equals(Util.class.getName())) {
               break;
            }
         }
         message.append("---\n");
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end(message.toString());
      }
   }

   @Override
   public void addBenchmark$multipart_form_data(RoutingContext ctx, String ifMatch, String storedFilesBenchmark) {
      String source = null;
      BenchmarkData data = new ProvidedBenchmarkData();
      for (FileUpload upload : ctx.fileUploads()) {
         byte[] bytes;
         try {
            bytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));
         } catch (IOException e) {
            log.error("Cannot read uploaded file {}", upload.uploadedFileName(), e);
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
            return;
         }
         if (upload.name().equals("benchmark")) {
            try {
               source = new String(bytes, upload.charSet());
            } catch (UnsupportedEncodingException e) {
               source = new String(bytes, StandardCharsets.UTF_8);
            }
         } else {
            ((ProvidedBenchmarkData) data).files.put(upload.fileName(), bytes);
         }
      }
      if (source == null) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .end("Multi-part definition missing benchmark=source-file.yaml");
         return;
      }
      try {
         if (storedFilesBenchmark != null) {
            // sanitize to prevent directory escape
            storedFilesBenchmark = BenchmarkData.sanitize(storedFilesBenchmark);
            Path dataDirPath = Controller.BENCHMARK_DIR.resolve(storedFilesBenchmark + ".data");
            log.info("Trying to use stored files from {}, adding files from request: {}", dataDirPath, data.files().keySet());
            if (!data.files().isEmpty()) {
               File dataDir = dataDirPath.toFile();
               //noinspection ResultOfMethodCallIgnored
               dataDir.mkdirs();
               if (dataDir.exists() && dataDir.isDirectory()) {
                  try {
                     PersistedBenchmarkData.store(data.files(), dataDirPath);
                  } catch (IOException e) {
                     ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                           .end("Failed to store benchmark files.");
                  }
               }
            }
            data = new PersistedBenchmarkData(dataDirPath);
         }
         addBenchmarkAndReply(ctx, source, data, ifMatch);
      } catch (ParserException | BenchmarkDefinitionException e) {
         respondParsingError(ctx, e);
      }
   }

   @Override
   public void getBenchmark$text_vnd_yaml(RoutingContext ctx, String name) {
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         BenchmarkSource template = controller.getTemplate(name);
         if (template == null) {
            ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("No benchmark or template '" + name + "'.");
         } else {
            sendYamlBenchmark(ctx, template, template.version);
         }
      } else {
         sendYamlBenchmark(ctx, benchmark.source(), benchmark.version());
      }
   }

   private void sendYamlBenchmark(RoutingContext ctx, BenchmarkSource source, String version) {
      if (source == null) {
         ctx.response()
               .setStatusCode(HttpResponseStatus.NOT_ACCEPTABLE.code())
               .end("Benchmark does not preserve the original source.");
      } else {
         HttpServerResponse response = ctx.response()
               .putHeader(HttpHeaders.CONTENT_TYPE, "text/vnd.yaml; charset=UTF-8")
               .putHeader(HttpHeaders.ETAG.toString(), version);
         source.data.files().keySet().forEach(file -> response.putHeader("x-file", file));
         response.end(source.yaml);
      }
   }

   @Override
   public void getBenchmark$application_java_serialized_object(RoutingContext ctx, String name) {
      withBenchmark(ctx, name, benchmark -> sendSerializedBenchmark(ctx, benchmark));
   }

   @Override
   public void deleteBenchmark(RoutingContext ctx, String name) {
      try {
         if (controller.deleteBenchmark(name)) {
            ctx.response().setStatusCode(204).end();
         } else {
            ctx.response().setStatusCode(404).end("Could not find benchmark " + name);
         }
      } catch (Throwable t) {
         ctx.response().setStatusCode(500).end(t.getMessage());
      }
   }

   private void sendSerializedBenchmark(RoutingContext ctx, Benchmark benchmark) {
      try {
         byte[] bytes = Util.serialize(benchmark);
         ctx.response()
               .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_SERIALIZED)
               .end(Buffer.buffer(bytes));
      } catch (IOException e) {
         log.error("Failed to serialize", e);
         ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end("Error encoding benchmark.");
      }
   }

   @Override
   public void startBenchmark(RoutingContext ctx, String name, String desc, String xTriggerJob, String runId,
         List<String> templateParam) {
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         BenchmarkSource template = controller.getTemplate(name);
         if (template == null) {
            ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("Benchmark not found");
            return;
         }
         benchmark = templateToBenchmark(ctx, template, templateParam);
         if (benchmark == null) {
            return;
         }
      }
      String triggerUrl = benchmark.triggerUrl() != null ? benchmark.triggerUrl() : TRIGGER_URL;
      if (triggerUrl != null) {
         if (xTriggerJob == null) {
            Run run = controller.createRun(benchmark, desc);
            if (!triggerUrl.endsWith("&") && !triggerUrl.endsWith("?")) {
               if (triggerUrl.contains("?")) {
                  triggerUrl = triggerUrl + "&";
               } else {
                  triggerUrl = triggerUrl + "?";
               }
            }
            ctx.response()
                  .setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code())
                  .putHeader(HttpHeaders.LOCATION, triggerUrl + "BENCHMARK=" + name + "&RUN_ID=" + run.id)
                  .putHeader("x-run-id", run.id)
                  .end("This controller is configured to trigger jobs through CI instance.");
            return;
         }
      }
      Run run;
      if (runId == null) {
         run = controller.createRun(benchmark, desc);
      } else {
         run = controller.run(runId);
         if (run == null || run.startTime != Long.MIN_VALUE) {
            ctx.response().setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end("Run already started");
            return;
         }
      }
      String error = controller.startBenchmark(run);
      if (error == null) {
         ctx.response().setStatusCode(HttpResponseStatus.ACCEPTED.code())
               .putHeader(HttpHeaders.LOCATION, baseURL + "/run/" + run.id)
               .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON)
               .end(Json.encodePrettily(runInfo(run, false)));
      } else {
         ctx.response()
               .setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end(error);
      }
   }

   private Benchmark templateToBenchmark(RoutingContext ctx, BenchmarkSource template, List<String> templateParam) {
      Map<String, String> paramMap = new HashMap<>();
      for (String item : templateParam) {
         int index = item.indexOf("=");
         if (index < 0) {
            paramMap.put(item, "");
         } else {
            paramMap.put(item.substring(0, index), item.substring(index + 1));
         }
      }
      List<String> missingParams = template.paramsWithDefaults.entrySet().stream()
            .filter(entry -> entry.getValue() == null).map(Map.Entry::getKey)
            .filter(param -> !paramMap.containsKey(param)).collect(Collectors.toList());
      if (missingParams.isEmpty()) {
         try {
            return BenchmarkParser.instance().buildBenchmark(template, paramMap);
         } catch (BenchmarkData.MissingFileException e) {
            ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                  .end("This benchmark is a template; external files are not uploaded for templates and the run command must append them when the benchmark is first run.");
            return null;
         } catch (ParserException | BenchmarkDefinitionException e) {
            ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end(Util.explainCauses(e));
            return null;
         }
      } else {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end(
               "Benchmark " + template.name + " is missing these mandatory parameters: " + missingParams);
         return null;
      }
   }

   @Override
   public void getBenchmarkStructure(RoutingContext ctx, String name, int maxCollectionSize, List<String> templateParam) {
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         BenchmarkSource template = controller.getTemplate(name);
         if (template == null) {
            ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("No benchmark or template'" + name + "'.");
         } else {
            String content = null;
            if (!templateParam.isEmpty()) {
               benchmark = templateToBenchmark(ctx, template, templateParam);
               if (benchmark == null) {
                  return;
               } else {
                  content = createStructure(maxCollectionSize, benchmark);
               }
            }
            respondWithJson(ctx, false, new Client.BenchmarkStructure(template.paramsWithDefaults, content));
         }
      } else {
         String content = createStructure(maxCollectionSize, benchmark);
         respondWithJson(ctx, false, new Client.BenchmarkStructure(Collections.emptyMap(), content));
      }
   }

   @Override
   public void getBenchmarkFiles(RoutingContext ctx, String name) {
      Benchmark benchmark = controller.getBenchmark(name);
      Map<String, byte[]> files;
      if (benchmark == null) {
         BenchmarkSource template = controller.getTemplate(name);
         if (template == null) {
            ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("No benchmark or template '" + name + "'");
            return;
         } else {
            files = template.data.files();
         }
      } else {
         files = benchmark.files();
      }
      ThreadLocalRandom random = ThreadLocalRandom.current();
      String boundary = new UUID(random.nextLong(), random.nextLong()).toString();
      HttpServerResponse response = ctx.response();
      response.putHeader(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=\"" + boundary + "\"");
      response.setChunked(true);
      response.write("--" + boundary);
      for (var file : files.entrySet()) {
         response.write("\n");
         response.write(HttpHeaders.CONTENT_TYPE + ": application/octet-stream\n");
         response.write(HttpHeaders.CONTENT_LENGTH + ": " + file.getValue().length + "\n");
         response.write(HttpHeaders.CONTENT_DISPOSITION + ": form-data; name=\"file\"; filename=\"" + file.getKey() + "\"\n\n");
         response.write(Buffer.buffer(file.getValue()));
         response.write("\n--" + boundary);
      }
      response.write("--");
      response.end();
   }

   private String createStructure(int maxCollectionSize, Benchmark benchmark) {
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(byteStream, true, StandardCharsets.UTF_8)) {
         new YamlVisitor(stream, maxCollectionSize).walk(benchmark);
      }
      return byteStream.toString(StandardCharsets.UTF_8);
   }

   @Override
   public void listRuns(RoutingContext ctx, boolean details) {
      io.hyperfoil.controller.model.Run[] runs = controller.runs().stream()
            .map(r -> details ? runInfo(r, false)
                  : new io.hyperfoil.controller.model.Run(r.id, null, null, null, r.cancelled,
                        r.completed, r.persisted, null, null, null, null))
            .toArray(io.hyperfoil.controller.model.Run[]::new);
      respondWithJson(ctx, true, runs);
   }

   @Override
   public void getRun(RoutingContext ctx, String runId) {
      withRun(ctx, runId, run -> respondWithJson(ctx, true, runInfo(run, true)));
   }

   @Override
   public void agentCpu(RoutingContext ctx, String runId) {
      withStats(ctx, runId, run -> respondWithJson(ctx, false, run.statisticsStore().cpuUsage()));
   }

   private io.hyperfoil.controller.model.Run runInfo(Run run, boolean reportPhases) {
      String benchmark = null;
      if (run.benchmark != null) {
         benchmark = run.benchmark.name();
      }

      Date started = null, terminated = null;
      if (run.startTime > Long.MIN_VALUE) {
         started = new Date(run.startTime);
      }
      if (run.terminateTime.future().isComplete()) {
         terminated = new Date(run.terminateTime.future().result());
      }
      List<io.hyperfoil.controller.model.Phase> phases = null;
      if (reportPhases) {
         long now = System.currentTimeMillis();
         phases = run.phases.values().stream()
               .filter(p -> !(p.definition().model instanceof Model.Noop))
               .sorted(PHASE_COMPARATOR)
               .map(phase -> {
                  Date phaseStarted = null, phaseTerminated = null;
                  StringBuilder remaining = null;
                  StringBuilder totalDuration = null;
                  if (phase.absoluteStartTime() > Long.MIN_VALUE) {
                     phaseStarted = new Date(phase.absoluteStartTime());
                     if (!phase.status().isTerminated()) {
                        remaining = new StringBuilder()
                              .append(phase.definition().duration() - (now - phase.absoluteStartTime())).append(" ms");
                        if (phase.definition().maxDuration() >= 0) {
                           remaining.append(" (")
                                 .append(phase.definition().maxDuration() - (now - phase.absoluteStartTime())).append(" ms)");
                        }
                     } else {
                        phaseTerminated = new Date(phase.absoluteCompletionTime());
                        long totalDurationValue = phase.absoluteCompletionTime() - phase.absoluteStartTime();
                        totalDuration = new StringBuilder().append(totalDurationValue).append(" ms");
                        if (totalDurationValue > phase.definition().duration()) {
                           totalDuration.append(" (exceeded by ").append(totalDurationValue - phase.definition().duration())
                                 .append(" ms)");
                        }
                     }
                  }
                  String type = phase.definition().getClass().getSimpleName();
                  type = Character.toLowerCase(type.charAt(0)) + type.substring(1);
                  return new io.hyperfoil.controller.model.Phase(phase.definition().name(), phase.status().toString(), type,
                        phaseStarted, remaining == null ? null : remaining.toString(),
                        phaseTerminated, phase.isFailed(), totalDuration == null ? null : totalDuration.toString(),
                        phase.definition().description());
               }).collect(Collectors.toList());
      }
      List<io.hyperfoil.controller.model.Agent> agents = run.agents.stream()
            .map(ai -> new io.hyperfoil.controller.model.Agent(ai.name, ai.deploymentId, ai.status.toString()))
            .collect(Collectors.toList());
      return new io.hyperfoil.controller.model.Run(run.id, benchmark, started, terminated, run.cancelled, run.completed,
            run.persisted, run.description, phases, agents,
            run.errors.stream().map(Run.Error::toString).collect(Collectors.toList()));
   }

   private void withRun(RoutingContext ctx, String runId, Consumer<Run> consumer) {
      Run run;
      if ("last".equals(runId)) {
         run = controller.runs.values().stream()
               .reduce(LAST_RUN_OPERATOR)
               .orElse(null);
      } else {
         run = controller.run(runId);
      }
      if (run == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
      } else {
         consumer.accept(run);
      }
   }

   @Override
   public void killRun(RoutingContext ctx, String runId) {
      withRun(ctx, runId, run -> controller.kill(run, result -> {
         if (result.succeeded()) {
            ctx.response().setStatusCode(HttpResponseStatus.ACCEPTED.code()).end();
         } else {
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                  .setStatusMessage(result.cause().getMessage()).end();
         }
      }));
   }

   @Override
   public void createReport(RoutingContext ctx, String runId, String source) {
      withRun(ctx, runId, run -> {
         StringBuilder template = new StringBuilder();
         String providedTemplatePath = Properties.get(Properties.REPORT_TEMPLATE, "");

         if (providedTemplatePath.isBlank()) {
            // use the embedded template html
            try (InputStream stream = FileLookupFactory.newInstance().lookupFile("report-template.html",
                  Thread.currentThread().getContextClassLoader());
                  BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
               String line;
               while ((line = reader.readLine()) != null) {
                  template.append(line).append("\n");
               }
            } catch (IOException e) {
               log.error("Cannot read report template: ", e);
               ctx.response().setStatusCode(500).end();
               return;
            }
         } else {
            // use the provided template
            log.info("Using the provided report template at {}", providedTemplatePath);
            File templateFile = Path.of(providedTemplatePath).toFile();
            if (templateFile.exists() && templateFile.isFile()) {
               try {
                  template.append(Files.readString(templateFile.toPath(), StandardCharsets.UTF_8));
               } catch (IOException e) {
                  log.error("Cannot read report template: ", e);
                  ctx.response().setStatusCode(500).end();
                  return;
               }
            } else {
               log.error("Template file is not available.");
               ctx.response().setStatusCode(500).end();
               return;
            }
         }

         String sourceFile = source != null ? source : ControllerVerticle.DEFAULT_STATS_JSON;
         Path runDir = controller.getRunDir(run).toAbsolutePath();
         Path filePath = runDir.resolve(sourceFile).toAbsolutePath();
         if (!filePath.startsWith(runDir)) {
            ctx.response().setStatusCode(403).end("Requested file is not within the run directory!");
         } else if (!filePath.toFile().exists()) {
            ctx.response().setStatusCode(404).end("Requested file was not found");
         } else {
            try {
               String json = Files.readString(filePath);
               int placeholderIndex = template.indexOf(DATAKEY);
               HttpServerResponse response = ctx.response()
                     .putHeader(HttpHeaders.CONTENT_TYPE, "text/html").setChunked(true);
               response.write(template.substring(0, placeholderIndex));
               response.write(json);
               response.write(template.substring(placeholderIndex + DATAKEY.length()));
               response.end();
            } catch (IOException e) {
               log.error("Cannot read file {}", filePath);
               ctx.response().setStatusCode(500).end("Cannot fetch file " + sourceFile);
            }
         }
      });
   }

   @Override
   public void listSessions(RoutingContext ctx, String runId, boolean inactive) {
      withRun(ctx, runId, run -> {
         ctx.response().setChunked(true);
         controller.listSessions(run, inactive,
               (agent, session) -> {
                  String line = agent.name + ": " + session + "\n";
                  ctx.response().write(Buffer.buffer(line.getBytes(StandardCharsets.UTF_8)));
               },
               commonListingHandler(ctx.response()));
      });
   }

   private Handler<AsyncResult<Void>> commonListingHandler(HttpServerResponse response) {
      return result -> {
         if (result.succeeded()) {
            response.setStatusCode(HttpResponseStatus.OK.code()).end();
         } else if (result.cause() instanceof NoStackTraceThrowable) {
            response.setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         } else {
            response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(result.cause().getMessage());
         }
      };
   }

   @Override
   public void getRecentSessions(RoutingContext ctx, String runId) {
      getSessionStats(ctx, runId, ss -> ss.recentSessionPoolSummary(System.currentTimeMillis() - 5000));
   }

   @Override
   public void getTotalSessions(RoutingContext ctx, String runId) {
      getSessionStats(ctx, runId, StatisticsStore::totalSessionPoolSummary);
   }

   private void getSessionStats(RoutingContext ctx, String runId,
         Function<StatisticsStore, Map<String, Map<String, LowHigh>>> func) {
      withStats(ctx, runId, run -> {
         Map<String, Map<String, LowHigh>> stats = func.apply(run.statisticsStore());
         JsonObject reply = new JsonObject();
         for (Map.Entry<String, Map<String, LowHigh>> entry : stats.entrySet()) {
            String phase = entry.getKey();
            Map<String, LowHigh> addressStats = entry.getValue();
            JsonObject phaseStats = new JsonObject();
            reply.put(phase, phaseStats);
            addressStats.forEach((address, lowHigh) -> {
               String agent = run.agents.stream().filter(a -> a.deploymentId.equals(address)).map(a -> a.name).findFirst()
                     .orElse("unknown");
               phaseStats.put(agent, new JsonObject().put("min", lowHigh.low).put("max", lowHigh.high));
            });
         }
         respondWithJson(ctx, reply);
      });
   }

   @Override
   public void listConnections(RoutingContext ctx, String runId) {
      withRun(ctx, runId, run -> {
         ctx.response().setChunked(true);
         controller.listConnections(run,
               (agent, connection) -> {
                  String line = agent.name + ": " + connection + "\n";
                  ctx.response().write(Buffer.buffer(line.getBytes(StandardCharsets.UTF_8)));
               },
               commonListingHandler(ctx.response()));
      });
   }

   @Override
   public void getRecentConnections(RoutingContext ctx, String runId) {
      connectionStats(ctx, runId, StatisticsStore::recentConnectionsSummary);
   }

   @Override
   public void getTotalConnections(RoutingContext ctx, String runId) {
      connectionStats(ctx, runId, StatisticsStore::totalConnectionsSummary);
   }

   private void connectionStats(RoutingContext ctx, String runId,
         Function<StatisticsStore, Map<String, Map<String, LowHigh>>> mapper) {
      withStats(ctx, runId, run -> {
         Map<String, Map<String, LowHigh>> stats = mapper.apply(run.statisticsStore());
         JsonObject result = stats.entrySet().stream().collect(JsonObject::new,
               (json, e) -> json.put(e.getKey(), lowHighMapToJson(e.getValue())), JsonObject::mergeIn);
         respondWithJson(ctx, JsonObject.mapFrom(result));
      });
   }

   private static JsonObject lowHighMapToJson(Map<String, LowHigh> map) {
      return map.entrySet().stream().collect(JsonObject::new,
            (byType, e2) -> byType.put(e2.getKey(),
                  new JsonObject().put("min", e2.getValue().low).put("max", e2.getValue().high)),
            JsonObject::mergeIn);
   }

   @Override
   public void getAllStats$application_zip(RoutingContext ctx, String runId) {
      getAllStatsCsv(ctx, runId);
   }

   @Override
   public void getAllStatsCsv(RoutingContext ctx, String runId) {
      withTerminatedRun(ctx, runId, run -> new Zipper(ctx.response(),
            controller.getRunDir(run).resolve("stats")).run());
   }

   @Override
   public void getAllStats$application_json(RoutingContext ctx, String runId) {
      getAllStatsJson(ctx, runId);
   }

   @Override
   public void getAllStatsJson(RoutingContext ctx, String runId) {
      withTerminatedRun(ctx, runId, run -> ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON)
            .sendFile(controller.getRunDir(run).resolve(ControllerVerticle.DEFAULT_STATS_JSON).toString()));
   }

   private void withTerminatedRun(RoutingContext ctx, String runId, Consumer<Run> consumer) {
      withRun(ctx, runId, run -> {
         if (!run.terminateTime.future().isComplete()) {
            ctx.response().setStatusCode(HttpResponseStatus.SEE_OTHER.code())
                  .setStatusMessage("Run is not completed yet.")
                  .putHeader(HttpHeaders.LOCATION, "/run/" + run.id)
                  .end();
         } else {
            consumer.accept(run);
         }
      });
   }

   @Override
   public void getRecentStats(RoutingContext ctx, String runId) {
      withStats(ctx, runId, run -> {
         List<RequestStats> stats = run.statisticsStore().recentSummary(System.currentTimeMillis() - 5000);
         respondWithJson(ctx, false, statsToJson(run, stats));
      });
   }

   @Override
   public void getTotalStats(RoutingContext ctx, String runId) {
      withStats(ctx, runId, run -> {
         List<RequestStats> stats = run.statisticsStore().totalSummary();
         respondWithJson(ctx, false, statsToJson(run, stats));
      });
   }

   @Override
   public void getHistogramStats(RoutingContext ctx, String runId, String phase, int stepId, String metric) {
      withStats(ctx, runId, run -> {
         Histogram histogram = run.statisticsStore().histogram(phase, stepId, metric);
         respondWithJson(ctx, false, histogram);
      });
   }

   @Override
   public void getSeries(RoutingContext ctx, String runId, String phase, int stepId, String metric) {
      withStats(ctx, runId, run -> {
         List<StatisticsSummary> series = run.statisticsStore().series(phase, stepId, metric);
         respondWithJson(ctx, false, series);
      });
   }

   @Override
   public void getRunFile(RoutingContext ctx, String runId, String file) {
      withRun(ctx, runId, run -> {
         Path runDir = controller.getRunDir(run).toAbsolutePath();
         Path path = runDir.resolve(file).toAbsolutePath();
         if (!path.startsWith(runDir)) {
            ctx.response().setStatusCode(403).end("Requested file is not within the run directory!");
         } else if (!path.toFile().exists() || !path.toFile().isFile()) {
            ctx.response().setStatusCode(404).end("Requested file was not found");
         } else {
            ctx.response().sendFile(path.toString());
         }
      });
   }

   private void withStats(RoutingContext ctx, String runId, Consumer<Run> consumer) {
      withRun(ctx, runId, run -> {
         if (run.statisticsStore() == null) {
            ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         } else {
            consumer.accept(run);
         }
      });
   }

   private io.hyperfoil.controller.model.RequestStatisticsResponse statsToJson(Run run, List<RequestStats> stats) {
      String status;
      if (run.terminateTime.future().isComplete()) {
         status = "TERMINATED";
      } else if (run.startTime > Long.MIN_VALUE) {
         status = "RUNNING";
      } else {
         status = "INITIALIZING";
      }
      return new io.hyperfoil.controller.model.RequestStatisticsResponse(status, stats);
   }

   @Override
   public void getBenchmarkForRun$text_vnd_yaml(RoutingContext ctx, String runId) {
      withRun(ctx, runId, run -> {
         try {
            Benchmark benchmark = controller.ensureBenchmark(run);
            sendYamlBenchmark(ctx, benchmark.source(), benchmark.version());
         } catch (ParserException e) {
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(Util.explainCauses(e));
         }
      });
   }

   @Override
   public void getBenchmarkForRun$application_java_serialized_object(RoutingContext ctx, String runId) {
      withRun(ctx, runId, run -> {
         try {
            sendSerializedBenchmark(ctx, controller.ensureBenchmark(run));
         } catch (ParserException e) {
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(Util.explainCauses(e));
         }
      });
   }

   @Override
   public void listAgents(RoutingContext ctx) {
      respondWithJson(ctx, true, new JsonArray(controller.runs.values().stream()
            .flatMap(run -> run.agents.stream().map(agentInfo -> agentInfo.name))
            .distinct().collect(Collectors.toList())));
   }

   @Override
   public void getControllerLog(RoutingContext ctx, long offset, long maxLength, String ifMatch) {
      if (maxLength < 0) {
         maxLength = Long.MAX_VALUE;
      }
      String logPath = Properties.get(Properties.CONTROLLER_LOG, controller.getConfig().getString(Properties.CONTROLLER_LOG));
      if (ifMatch != null && !ifMatch.equals(controller.deploymentID())) {
         ctx.response().setStatusCode(HttpResponseStatus.PRECONDITION_FAILED.code()).end();
         return;
      }
      if (controller.hasControllerLog()) {
         try {
            File tempFile = File.createTempFile("controller.", ".log");
            tempFile.deleteOnExit();
            controller.downloadControllerLog(offset, maxLength, tempFile, result -> {
               if (result.succeeded()) {
                  sendFile(ctx, tempFile, controller.deploymentID());
               } else {
                  log.error("Failed to download controller log.", result.cause());
                  ctx.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .setStatusMessage("Cannot download controller log").end();
               }
            });
         } catch (IOException e) {
            log.error("Failed to create temporary file", e);
            ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
         }
      } else {
         if (logPath == null || "/dev/null".equals(logPath)) {
            ctx.response()
                  .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                  .setStatusMessage("Log file not defined.").end();
            return;
         }
         File logFile = new File(logPath);
         if (!logFile.exists()) {
            ctx.response()
                  .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                  .setStatusMessage("Log file does not exist.").end();
         } else {
            if (offset < 0) {
               ctx.response()
                     .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                     .setStatusMessage("Offset must be non-negative").end();
            } else {
               ctx.response().putHeader(HttpHeaders.ETAG, controller.deploymentID());
               ctx.response().sendFile(logPath, offset, maxLength);
            }
         }
      }
   }

   private void sendFile(RoutingContext ctx, File tempFile, String etag) {
      //noinspection ResultOfMethodCallIgnored
      ctx.response()
            .putHeader(HttpHeaders.ETAG, etag)
            .sendFile(tempFile.toString(), r -> tempFile.delete());
   }

   @Override
   public void getAgentLog(RoutingContext ctx, String agent, long offset, long maxLength, String ifMatch) {
      if (agent == null || "controller".equals(agent)) {
         getControllerLog(ctx, offset, maxLength, ifMatch);
         return;
      }
      if (maxLength < 0) {
         maxLength = Long.MAX_VALUE;
      }
      if (offset < 0) {
         ctx.response()
               .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .setStatusMessage("Offset must be non-negative").end();
         return;
      }
      Optional<AgentInfo> agentInfo = controller.runs.values().stream()
            .reduce(LAST_RUN_OPERATOR)
            .flatMap(run -> run.agents.stream().filter(ai -> agent.equals(ai.name)).findFirst());
      if (agentInfo.isEmpty()) {
         ctx.response()
               .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
               .setStatusMessage("Agent " + agent + " not found.").end();
         return;
      }
      if (ifMatch != null && !ifMatch.equals(agentInfo.get().deploymentId)) {
         ctx.response().setStatusCode(HttpResponseStatus.PRECONDITION_FAILED.code()).end();
         return;
      }
      try {
         File tempFile = File.createTempFile("agent." + agent, ".log");
         tempFile.deleteOnExit();
         controller.downloadAgentLog(agentInfo.get().deployedAgent, offset, maxLength, tempFile, result -> {
            if (result.succeeded()) {
               sendFile(ctx, tempFile, agentInfo.get().deploymentId);
            } else {
               log.error("Failed to download agent log for {}", agentInfo.get(), result.cause());
               ctx.response()
                     .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                     .setStatusMessage("Cannot download agent log").end();
            }
         });
      } catch (IOException e) {
         log.error("Failed to create temporary file", e);
         ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
      }
   }

   @Override
   public void shutdown(RoutingContext ctx, boolean force) {
      List<Run> runs = controller.runs.values().stream().filter(run -> !run.terminateTime.future().isComplete())
            .collect(Collectors.toList());
      if (force) {
         // We don't allow concurrent runs ATM, but...
         @SuppressWarnings("rawtypes")
         List<Future> futures = new ArrayList<>();
         for (Run run : runs) {
            Promise<Void> promise = Promise.promise();
            futures.add(promise.future());
            controller.kill(run, result -> promise.complete());
         }
         CompositeFuture.all(futures).onComplete(nil -> {
            ctx.response().end();
            controller.shutdown();
         });
      } else if (runs.isEmpty()) {
         ctx.response().end();
         controller.shutdown();
      } else {
         String running = runs.stream().map(run -> run.id).collect(Collectors.joining(", "));
         ctx.response()
               .setStatusCode(HttpResponseStatus.FORBIDDEN.code())
               .setStatusMessage("These runs are still in progress: " + running).end();
      }
   }

   @Override
   public void getToken(RoutingContext ctx) {
      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8").end(BEARER_TOKEN);
   }

   @Override
   public void getVersion(RoutingContext ctx) {
      respondWithJson(ctx, true, new io.hyperfoil.controller.model.Version(Version.VERSION, Version.COMMIT_ID,
            controller.deploymentID(), new Date()));
   }

   public void withBenchmark(RoutingContext ctx, String name, Consumer<Benchmark> consumer) {
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         String message = "No benchmark '" + name + "'.";
         BenchmarkSource template = controller.getTemplate(name);
         if (template != null) {
            message += " There is an existing template with this name, though.";
         }
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end(message);
         return;
      }
      consumer.accept(benchmark);
   }

   private static class BasicAuthHandler implements Handler<RoutingContext> {
      @Override
      public void handle(RoutingContext ctx) {
         String authorization = ctx.request().getHeader(HttpHeaders.AUTHORIZATION);
         if (authorization != null && authorization.startsWith("Basic ")) {
            byte[] credentials = Base64.getDecoder().decode(authorization.substring(6).trim());
            for (int i = 0; i < credentials.length; ++i) {
               if (credentials[i] == ':') {
                  String password = new String(credentials, i + 1, credentials.length - i - 1, StandardCharsets.UTF_8);
                  if (password.equals(CONTROLLER_PASSWORD)) {
                     ctx.next();
                     return;
                  }
                  break;
               }
            }
            ctx.response().setStatusCode(403).end();
         } else if (authorization != null && authorization.startsWith("Bearer ")) {
            if (BEARER_TOKEN.equals(authorization.substring(7))) {
               ctx.next();
            } else {
               ctx.response().setStatusCode(403).end();
            }
         } else {
            ctx.response().setStatusCode(401).putHeader("WWW-Authenticate", "Basic realm=\"Hyperfoil\", charset=\"UTF-8\"")
                  .end();
         }
      }
   }
}
