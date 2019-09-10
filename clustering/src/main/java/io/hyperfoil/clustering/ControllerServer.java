package io.hyperfoil.clustering;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.client.Client;
import io.hyperfoil.core.Version;
import io.hyperfoil.core.util.LowHigh;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

class ControllerServer {
   private static final Logger log = LoggerFactory.getLogger(ControllerServer.class);
   private static final String MIME_TYPE_SERIALIZED = "application/java-serialized-object";
   private static final String MIME_TYPE_MULTIPART = "multipart/form-data";
   private static final Set<String> MIME_TYPE_YAML = new HashSet<>(
         Arrays.asList("text/vnd.yaml", "text/yaml", "text/x-yaml", "application/x-yaml"));
   private static final String MIME_TYPE_JSON = "application/json";

   private static final String CONTROLLER_HOST = Properties.get(Properties.CONTROLLER_HOST, "localhost");
   private static final int CONTROLLER_PORT = Properties.getInt(Properties.CONTROLLER_PORT, 8090);
   private static final String BASE_URL = "http://" + CONTROLLER_HOST + ":" + CONTROLLER_PORT;
   private static final Comparator<ControllerPhase> PHASE_COMPARATOR =
         Comparator.<ControllerPhase, Long>comparing(ControllerPhase::absoluteStartTime).thenComparing(p -> p.definition().name);

   private final ControllerVerticle controller;
   private final HttpServer httpServer;
   private final Router router;

   ControllerServer(ControllerVerticle controller) {
      this.controller = controller;
      router = Router.router(controller.getVertx());

      router.route().handler(BodyHandler.create());
      router.get("/").handler(this::handleIndex);
      router.post("/benchmark").handler(this::handlePostBenchmark);
      router.get("/benchmark").handler(this::handleListBenchmarks);
      router.get("/benchmark/:benchmarkname").handler(this::handleGetBenchmark);
      router.get("/benchmark/:benchmarkname/start").handler(this::handleBenchmarkStart);
      router.get("/run").handler(this::handleListRuns);
      router.get("/run/:runid").handler(this::handleGetRun);
      router.get("/run/:runid/kill").handler(this::handleRunKill);
      router.get("/run/:runid/sessions").handler(this::handleListSessions);
      router.get("/run/:runid/sessions/recent").handler(this::handleRecentSessions);
      router.get("/run/:runid/sessions/total").handler(this::handleTotalSessions);
      router.get("/run/:runid/connections").handler(this::handleListConnections);
      router.get("/run/:runid/stats/recent").handler(this::handleRecentStats);
      router.get("/run/:runid/stats/total").handler(this::handleTotalStats);
      router.get("/run/:runid/stats/custom").handler(this::handleCustomStats);
      router.get("/run/:runid/benchmark").handler(this::handleRunBenchmark);
      router.get("/agents").handler(this::handleAgents);
      router.get("/log").handler(this::handleLog);
      router.get("/log/:agent").handler(this::handleAgentLog);
      router.get("/shutdown").handler(this::handleShutdown);
      router.get("/version").handler(this::handleVersion);

      httpServer = controller.getVertx().createHttpServer().requestHandler(router).listen(CONTROLLER_PORT);
   }

   void stop(Future<Void> stopFuture) {
      httpServer.close(result -> stopFuture.complete());
   }

   private void handleIndex(RoutingContext ctx) {
      StringBuilder sb = new StringBuilder("Hello from Hyperfoil, these are available URLs:\n");
      for (Route route : router.getRoutes()) {
         if (route.getPath() != null) { // avoid the default route
            sb.append(route.getPath()).append('\n');
         }
      }
      ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain")
            .putHeader("x-epoch-millis", String.valueOf(System.currentTimeMillis()))
            .end(sb.toString());
   }

   private void handlePostBenchmark(RoutingContext ctx) {
      String ctHeader = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
      String contentType = ctHeader == null ? "text/vnd.yaml" : ctHeader.trim();
      Charset charset = StandardCharsets.UTF_8;
      int semicolonIndex = contentType.indexOf(';');
      if (semicolonIndex >= 0) {
         String tmp = contentType.substring(semicolonIndex + 1).trim();
         if (tmp.startsWith("charset=")) {
            charset = Charset.forName(tmp.substring(8));
         }
         contentType = contentType.substring(0, semicolonIndex).trim();
      }

      Benchmark benchmark;
      if (contentType.equals(MIME_TYPE_SERIALIZED)) {
         byte[] bytes = ctx.getBody().getBytes();
         try {
            benchmark = io.hyperfoil.util.Util.deserialize(bytes);
         } catch (IOException | ClassNotFoundException e) {
            log.error("Failed to serialize", e);
            ctx.response().setStatusCode(400).end("Cannot read benchmark.");
            return;
         }
      } else if (MIME_TYPE_YAML.contains(contentType) || MIME_TYPE_JSON.equals(contentType)) {
         String source = ctx.getBodyAsString(charset.name());
         try {
            benchmark = BenchmarkParser.instance().buildBenchmark(source, BenchmarkData.EMPTY);
         } catch (ParserException | BenchmarkDefinitionException e) {
            respondParsingError(ctx, e);
            return;
         }
      } else if (MIME_TYPE_MULTIPART.equals(contentType)) {
         String source = null;
         RequestBenchmarkData data = new RequestBenchmarkData();
         for (FileUpload upload : ctx.fileUploads()) {
            byte[] bytes;
            try {
               bytes = Files.readAllBytes(Paths.get(upload.uploadedFileName()));
            } catch (IOException e) {
               log.error("Cannot read uploaded file {}", e, upload.uploadedFileName());
               ctx.response().setStatusCode(500).end();
               return;
            }
            if (upload.name().equals("benchmark")) {
               try {
                  source = new String(bytes, upload.charSet());
               } catch (UnsupportedEncodingException e) {
                  source = new String(bytes, StandardCharsets.UTF_8);
               }
            } else {
               data.addFile(upload.fileName(), bytes);
            }
         }
         if (source == null) {
            ctx.response().setStatusCode(400).end("Multi-part definition missing benchmark=source-file.yaml");
            return;
         }
         try {
            benchmark = BenchmarkParser.instance().buildBenchmark(source, data);
         } catch (ParserException | BenchmarkDefinitionException e) {
            respondParsingError(ctx, e);
            return;
         }
      } else {
         ctx.response().setStatusCode(406).setStatusMessage("Unsupported Content-Type.");
         return;
      }

      if (benchmark != null) {
         String location = BASE_URL + "/benchmark/" + encode(benchmark.name());
         controller.addBenchmark(benchmark, event -> {
            if (event.succeeded()) {
               ctx.response().setStatusCode(204)
                     .putHeader(HttpHeaders.LOCATION, location).end();
            } else {
               ctx.response().setStatusCode(500).end();
            }
         });

      } else {
         ctx.response().setStatusCode(400).end("Cannot read benchmark.");
      }
   }

   private void respondParsingError(RoutingContext ctx, Exception e) {
      log.error("Failed to read benchmark", e);
      ctx.response().setStatusCode(400).end("Cannot read benchmark: " + Util.explainCauses(e));
   }

   private void handleListBenchmarks(RoutingContext routingContext) {
      routingContext.response().setStatusCode(200).end(Json.encodePrettily(controller.getBenchmarks()));
   }

   private void handleGetBenchmark(RoutingContext ctx) {
      String name = ctx.pathParam("benchmarkname");
      Benchmark benchmark = controller.getBenchmark(name);
      if (benchmark == null) {
         ctx.response().setStatusCode(404).setStatusMessage("No benchmark '" + name + "'").end();
         return;
      }

      sendBenchmark(ctx, benchmark);
   }

   private void handleRunBenchmark(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         return;
      }
      sendBenchmark(ctx, controller.ensureBenchmark(run));
   }

   private void sendBenchmark(RoutingContext ctx, Benchmark benchmark) {
      String acceptHeader = ctx.request().getHeader(HttpHeaders.ACCEPT);
      if (acceptHeader == null) {
         ctx.response().setStatusCode(400).setStatusMessage("Missing Accept header in the request.").end();
         return;
      }
      int semicolonIndex = acceptHeader.indexOf(';');
      if (semicolonIndex >= 0) {
         acceptHeader = acceptHeader.substring(0, semicolonIndex).trim();
      }
      if (acceptHeader.equals(MIME_TYPE_SERIALIZED)) {
         try {
            byte[] bytes = io.hyperfoil.util.Util.serialize(benchmark);
            ctx.response().setStatusCode(200)
                  .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_SERIALIZED)
                  .end(Buffer.buffer(bytes));
         } catch (IOException e) {
            log.error("Failed to serialize", e);
            ctx.response().setStatusCode(500).end("Error encoding benchmark.");
         }
      } else if (MIME_TYPE_YAML.contains(acceptHeader) || "*/*".equals(acceptHeader)) {
         if (benchmark.source() == null) {
            ctx.response().setStatusCode(406).setStatusMessage("Benchmark does not preserve the original source.");
         } else {
            ctx.response().setStatusCode(200)
                  .putHeader(HttpHeaders.CONTENT_TYPE, "text/vnd.yaml; charset=UTF-8")
                  .end(benchmark.source());
         }
      } else {
         ctx.response().setStatusCode(406).setStatusMessage("Unsupported type in Accept.").end();
      }
   }

   private void handleBenchmarkStart(RoutingContext routingContext) {
      String benchmarkName = routingContext.pathParam("benchmarkname");
      Benchmark benchmark = controller.getBenchmark(benchmarkName);
      List<String> descList = routingContext.queryParam("desc");
      String description = null;
      if (descList != null && !descList.isEmpty()) {
         description = descList.iterator().next();
      }
      if (benchmark != null) {
         ControllerVerticle.StartResult result = controller.startBenchmark(benchmark, description);
         if (result.runId != null) {
            routingContext.response().setStatusCode(HttpResponseStatus.ACCEPTED.code()).
                  putHeader(HttpHeaders.LOCATION, BASE_URL + "/run/" + result.runId)
                  .end("Starting benchmark " + benchmarkName + ", run ID " + result.runId);
         } else {
            routingContext.response()
                  .setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end(result.error);
         }
      } else {
         routingContext.response()
               .setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("Benchmark not found");
      }
   }

   private void handleListRuns(RoutingContext routingContext) {
      String[] ids = controller.runs().stream().map(run -> run.id).sorted().toArray(String[]::new);
      routingContext.response().setStatusCode(200).end(Json.encodePrettily(ids));
   }

   private void handleGetRun(RoutingContext routingContext) {
      Run run = getRun(routingContext);
      if (run == null) {
         routingContext.response().setStatusCode(404).end();
         return;
      }

      String benchmark = null;
      if (run.benchmark != null) {
         benchmark = run.benchmark.name();
      }

      Date started = null, terminated = null;
      if (run.startTime > Long.MIN_VALUE) {
         started = new Date(run.startTime);
      }
      if (run.terminateTime.isComplete()) {
         terminated = new Date(run.terminateTime.result());
      }
      long now = System.currentTimeMillis();
      List<Client.Phase> phases = run.phases.values().stream()
            .filter(p -> !(p.definition() instanceof Phase.Noop))
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
                  totalDuration.append(" (exceeded by ").append(totalDurationValue - phase.definition().duration()).append(" ms)");
               }
            }
         }
         String type = phase.definition().getClass().getSimpleName();
         type = Character.toLowerCase(type.charAt(0)) + type.substring(1);
         return new Client.Phase(phase.definition().name(), phase.status().toString(), type,
               phaseStarted, remaining == null ? null : remaining.toString(),
               phaseTerminated, totalDuration == null ? null : totalDuration.toString(),
               phase.definition().description());
      }).collect(Collectors.toList());
      List<Client.Agent> agents = run.agents.stream()
            .map(ai -> new Client.Agent(ai.name, ai.deploymentId, ai.status.toString()))
            .collect(Collectors.toList());
      Client.Run body = new Client.Run(run.id, benchmark, started, terminated, run.description, phases, agents,
            run.errors.stream().map(Run.Error::toString).collect(Collectors.toList()));
      String status = Json.encodePrettily(body);
      routingContext.response().end(status);
   }

   private Run getRun(RoutingContext routingContext) {
      String runid = routingContext.pathParam("runid");
      Run run;
      if ("last".equals(runid)) {
         run = controller.runs.values().stream().reduce((r1, r2) -> r1.startTime > r2.startTime ? r1 : r2).orElse(null);
      } else {
         run = controller.run(runid);
      }
      return run;
   }

   private void handleListSessions(RoutingContext routingContext) {
      HttpServerResponse response = routingContext.response().setChunked(true);
      boolean includeInactive = toBool(routingContext.queryParam("inactive"), false);
      Run run = getRun(routingContext);
      if (run == null) {
         routingContext.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
      }
      controller.listSessions(run, includeInactive,
            (agent, session) -> {
               String line = agent.name + ": " + session + "\n";
               response.write(Buffer.buffer(line.getBytes(StandardCharsets.UTF_8)));
            },
            commonListingHandler(response));
   }

   private boolean toBool(List<String> params, boolean defaultValue) {
      if (params.isEmpty()) {
         return defaultValue;
      }
      return "true".equals(params.get(params.size() - 1));
   }

   private void handleListConnections(RoutingContext routingContext) {
      HttpServerResponse response = routingContext.response().setChunked(true);
      Run run = getRun(routingContext);
      if (run == null) {
         routingContext.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
      }
      controller.listConnections(run,
            (agent, connection) -> {
               String line = agent.name + ": " + connection + "\n";
               response.write(Buffer.buffer(line.getBytes(StandardCharsets.UTF_8)));
            },
            commonListingHandler(response));
   }

   private Handler<AsyncResult<Void>> commonListingHandler(HttpServerResponse response) {
      return result -> {
         if (result.succeeded()) {
            response.setStatusCode(HttpResponseStatus.OK.code()).end();
         } else if (result.cause() instanceof NoStackTraceThrowable){
            response.setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         } else {
            response.setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end(result.cause().getMessage());
         }
      };
   }

   private void handleRunKill(RoutingContext routingContext) {
      Run run = getRun(routingContext);
      if (run != null) {
         controller.kill(run, result -> {
            if (result.succeeded()) {
               routingContext.response().setStatusCode(202).end();
            } else {
               routingContext.response().setStatusCode(500).setStatusMessage(result.cause().getMessage()).end();
            }
         });
      } else {
         routingContext.response().setStatusCode(404).end();
      }
   }

   private void handleRecentStats(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null || run.statisticsStore == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         return;
      }
      List<Client.RequestStats> stats = run.statisticsStore.recentSummary(System.currentTimeMillis() - 3000);
      ctx.response().end(Json.encodePrettily(statsToJson(run, stats)));
   }

   private Client.RequestStatisticsResponse statsToJson(Run run, List<Client.RequestStats> stats) {
      String status;
      if (run.terminateTime.isComplete()) {
         status = "TERMINATED";
      } else if (run.startTime > Long.MIN_VALUE) {
         status = "RUNNING";
      } else {
         status = "INITIALIZING";
      }
      return new Client.RequestStatisticsResponse(status, stats);
   }

   private void handleTotalStats(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null || run.statisticsStore == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         return;
      }
      List<Client.RequestStats> stats = run.statisticsStore.totalSummary();
      ctx.response().end(Json.encodePrettily(statsToJson(run, stats)));
   }

   private void handleCustomStats(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         return;
      } else if (run.statisticsStore == null) {
         ctx.response().end("{}");
         return;
      }
      List<Client.CustomStats> stats = run.statisticsStore.customStats();
      // TODO: add json response format based on 'Accept' header
      ctx.response().end(new JsonArray(stats).encodePrettily());
   }

   private void handleRecentSessions(RoutingContext ctx) {
      handleSessionPoolStats(ctx, run -> run.statisticsStore.recentSessionPoolSummary(System.currentTimeMillis() - 3000));
   }

   private void handleTotalSessions(RoutingContext ctx) {
      handleSessionPoolStats(ctx, run -> run.statisticsStore.totalSessionPoolSummary());
   }

   private void handleSessionPoolStats(RoutingContext ctx, Function<Run, Map<String, Map<String, LowHigh>>> func) {
      Run run = getRun(ctx);
      if (run == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end();
         return;
      } else if (run.statisticsStore == null) {
         ctx.response().end("{}");
         return;
      }
      Map<String, Map<String, LowHigh>> stats = func.apply(run);
      JsonObject reply = new JsonObject();
      stats.forEach((phase, addressStats) -> {
         JsonObject phaseStats = new JsonObject();
         reply.put(phase, phaseStats);
         addressStats.forEach((address, lowHigh) -> {
            String agent = run.agents.stream().filter(a -> a.deploymentId.equals(address)).map(a -> a.name).findFirst().orElse("unknown");
            phaseStats.put(agent, new JsonObject().put("min", lowHigh.low).put("max", lowHigh.high));
         });
      });
      ctx.response().end(reply.encodePrettily());
   }

   private void handleAgents(RoutingContext ctx) {
      ctx.response().end(new JsonArray(controller.runs.values().stream()
            .flatMap(run -> run.agents.stream().map(agentInfo -> agentInfo.name))
            .distinct().collect(Collectors.toList())).encodePrettily());
   }

   private void handleLog(RoutingContext ctx) {
      String logPath = System.getProperty(Properties.CONTROLLER_LOG);
      if (logPath == null) {
         ctx.response().setStatusCode(404).setStatusMessage("Log file not defined.").end();
         return;
      }
      File logFile = new File(logPath);
      if (!logFile.exists()) {
         ctx.response().setStatusCode(404).setStatusMessage("Log file does not exist.").end();
      } else {
         long offset = getOffset(ctx);
         if (offset >= 0) {
            ctx.response().putHeader(HttpHeaders.ETAG, controller.deploymentID());
            ctx.response().sendFile(logPath, offset);
         }
      }
   }

   private void handleAgentLog(RoutingContext ctx) {
      String agent = ctx.pathParam("agent");
      if (agent == null || "controller".equals(agent)) {
         handleLog(ctx);
         return;
      }
      long offset = getOffset(ctx);
      if (offset < 0) {
         return;
      }
      Optional<AgentInfo> agentInfo = controller.runs.values().stream()
            .sorted(Comparator.<Run, Long>comparing(run -> run.startTime).reversed())
            .flatMap(run -> run.agents.stream())
            .filter(ai -> agent.equals(ai.name)).findFirst();
      if (!agentInfo.isPresent()) {
         ctx.response().setStatusCode(404).setStatusMessage("Agent " + agent + " not found.").end();
         return;
      }
      try {
         File tempFile = File.createTempFile("agent." + agent, ".log");
         tempFile.deleteOnExit();
         controller.downloadAgentLog(agentInfo.get().deployedAgent, offset, tempFile, result -> {
            if (result.succeeded()) {
               ctx.response().putHeader(HttpHeaders.ETAG, agentInfo.get().deploymentId);
               ctx.response().sendFile(tempFile.toString(), r -> tempFile.delete());
            } else {
               log.error("Failed to download agent log for " + agentInfo.get(), result.cause());
               ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                     .setStatusMessage("Cannot download agent log").end();
            }
         });
      } catch (IOException e) {
         log.error("Failed to create temporary file", e);
         ctx.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
      }
   }

   private long getOffset(RoutingContext ctx) {
      long offset = 0;
      String offsetParam = ctx.request().getParam("offset");
      if (offsetParam != null) {
         try {
            offset = Long.parseLong(offsetParam);
         } catch (NumberFormatException e) {
            ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                  .setStatusMessage("Malformed offset").end();
            return -1;
         }
      }
      if (offset < 0) {
         ctx.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
               .setStatusMessage("Offset must be non-negative").end();
         return -1;
      }
      return offset;
   }

   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }

   private void handleShutdown(RoutingContext ctx) {
      boolean force = !ctx.queryParam("force").isEmpty();
      List<Run> runs = controller.runs.values().stream().filter(run -> !run.terminateTime.isComplete()).collect(Collectors.toList());
      if (force) {
         // We don't allow concurrent runs ATM, but...
         List<Future> futures = new ArrayList<>();
         for (Run run: runs) {
            Future<Void> future = Future.future();
            futures.add(future);
            controller.kill(run, result -> future.complete());
         }
         CompositeFuture.all(futures).setHandler(nil -> {
            ctx.response().setStatusCode(200).end();
            controller.shutdown();
         });
      } else if (runs.isEmpty()) {
         ctx.response().setStatusCode(200).end();
         controller.shutdown();
      } else {
         String running = runs.stream().map(run -> run.id).collect(Collectors.joining(", "));
         ctx.response().setStatusCode(403)
               .setStatusMessage("These runs are still in progress: " + running).end();
      }
   }

   private void handleVersion(RoutingContext ctx) {
      ctx.response().end(Json.encodePrettily(new Client.Version(Version.VERSION, Version.COMMIT_ID, controller.deploymentID())));
   }
}
