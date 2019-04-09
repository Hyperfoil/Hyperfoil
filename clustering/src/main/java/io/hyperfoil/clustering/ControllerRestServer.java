package io.hyperfoil.clustering;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.client.Client;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

class ControllerRestServer {
   private static final Logger log = LoggerFactory.getLogger(ControllerRestServer.class);
   private static final String MIME_TYPE_SERIALIZED = "application/java-serialized-object";
   private static final String MIME_TYPE_MULTIPART = "multipart/form-data";
   private static final Set<String> MIME_TYPE_YAML = new HashSet<>(
         Arrays.asList("text/vnd.yaml", "text/yaml", "text/x-yaml", "application/x-yaml"));

   private static final String CONTROLLER_HOST = System.getProperty(Properties.CONTROLLER_HOST, "localhost");
   private static final int CONTROLLER_PORT = Integer.parseInt(System.getProperty(Properties.CONTROLLER_PORT, "8090"));
   private static final String BASE_URL = "http://" + CONTROLLER_HOST + ":" + CONTROLLER_PORT;

   private final AgentControllerVerticle controller;
   private final HttpServer httpServer;
   private final Router router;

   ControllerRestServer(AgentControllerVerticle controller) {
      this.controller = controller;
      router = Router.router(controller.getVertx());

      router.route().handler(BodyHandler.create());
      router.get("/").handler(this::handleIndex);
      router.post("/benchmark").handler(this::handlePostBenchmark);
      router.get("/benchmark").handler(this::handleListBenchmarks);
      router.get("/benchmark/:benchmarkname").handler(this::handleGetBenchmark);
      router.get("/benchmark/:benchmarkname/start").handler(this::handleBenchmarkStart);
      router.get("/agents").handler(this::handleGetAgents);
      router.get("/run").handler(this::handleListRuns);
      router.get("/run/:runid").handler(this::handleGetRun);
      router.get("/run/:runid/kill").handler(this::handleRunKill);
      router.get("/run/:runid/sessions").handler(this::handleListSessions);
      router.get("/run/:runid/connections").handler(this::handleListConnections);
      router.get("/run/:runid/stats/recent").handler(this::handleRecentStats);
      router.get("/run/:runid/stats/total").handler(this::handleTotalStats);

      httpServer = controller.getVertx().createHttpServer().requestHandler(router::accept).listen(CONTROLLER_PORT);
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
      ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain").end(sb.toString());
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
      } else if (MIME_TYPE_YAML.contains(contentType)) {
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
      StringBuilder causes = new StringBuilder();
      Set<Throwable> reported = new HashSet<>();
      Throwable last = e;
      while (last != null && !reported.contains(last)) {
         if (causes.length() != 0) {
            causes.append(": ");
         }
         causes.append(last.getMessage());
         reported.add(last);
         last = last.getCause();
      }
      ctx.response().setStatusCode(400).end("Cannot read benchmark: " + causes);
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

      String acceptHeader = ctx.request().getHeader(HttpHeaders.ACCEPT);
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
         String runId = controller.startBenchmark(benchmark, description);
         if (runId != null) {
            routingContext.response().setStatusCode(HttpResponseStatus.ACCEPTED.code()).
                  putHeader(HttpHeaders.LOCATION, BASE_URL + "/run/" + runId)
                  .end("Starting benchmark " + benchmarkName + ", run ID " + runId);
         } else {
            routingContext.response()
                  .setStatusCode(HttpResponseStatus.FORBIDDEN.code()).end("Cannot start benchmark.");
         }
      } else {
         routingContext.response()
               .setStatusCode(HttpResponseStatus.NOT_FOUND.code()).end("Benchmark not found");
      }
   }

   private void handleGetAgents(RoutingContext routingContext) {
      Client.Agent[] agents = controller.agents.values().stream()
            .map(ai -> new Client.Agent(ai.name, ai.address, ai.status.toString()))
            .toArray(Client.Agent[]::new);
      routingContext.response().end(Json.encodePrettily(agents));
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

      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
      Date started = null, terminated = null;
      if (run.startTime > Long.MIN_VALUE) {
         started = new Date(run.startTime);
      }
      if (run.terminateTime > Long.MIN_VALUE) {
         terminated = new Date(run.terminateTime);
      }
      long now = System.currentTimeMillis();
      List<Client.Phase> phases = run.phases.values().stream().sorted(Comparator.comparing(p -> p.definition().name)).map(phase -> {
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
               phaseTerminated = new Date(phase.absoluteTerminateTime());
               long totalDurationValue = phase.absoluteTerminateTime() - phase.absoluteStartTime();
               totalDuration = new StringBuilder().append(totalDurationValue).append(" ms");
               if (totalDurationValue > phase.definition().duration()) {
                  totalDuration.append(" (exceeded by ").append(totalDurationValue - phase.definition().duration()).append(" ms)");
               }
            }
         }
         return new Client.Phase(phase.definition().name(), phase.status().toString(),
               phaseStarted, remaining == null ? null : remaining.toString(),
               phaseTerminated, totalDuration == null ? null : totalDuration.toString());
      }).collect(Collectors.toList());
      List<Client.Agent> agents = run.agents.stream()
            .map(ai -> new Client.Agent(ai.name, ai.address, ai.status.toString()))
            .collect(Collectors.toList());
      Client.Run body = new Client.Run(run.id, benchmark, started, terminated, run.description, phases, agents);
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
         controller.kill(run);
         routingContext.response().setStatusCode(202).end();
      } else {
         routingContext.response().setStatusCode(404).end();
      }
   }

   private void handleRecentStats(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null || run.statisticsStore == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code());
         return;
      }
      Map<String, Map<String, StatisticsSummary>> stats = run.statisticsStore.recentSummary(System.currentTimeMillis() - 3000);
      // TODO: add json response format based on 'Accept' header
      ctx.response().end(formatStatsSummary(stats));
   }

   private void handleTotalStats(RoutingContext ctx) {
      Run run = getRun(ctx);
      if (run == null || run.statisticsStore == null) {
         ctx.response().setStatusCode(HttpResponseStatus.NOT_FOUND.code());
         return;
      }
      Map<String, Map<String, StatisticsSummary>> stats = run.statisticsStore.totalSummary();
      // TODO: add json response format based on 'Accept' header
      ctx.response().end(formatStatsSummary(stats));
   }

   private String formatStatsSummary(Map<String, Map<String, StatisticsSummary>> stats) {
      StringBuilder sb = new StringBuilder();
      sb.append("Phase   Sequence");
      int longestSequenceName = Math.max(stats.values().stream().flatMap(m -> m.keySet().stream()).mapToInt(String::length).max().orElse(8), 8);
      printSpaces(sb, longestSequenceName - 8);
      sb.append("  Requests      Mean       p50       p90       p99     p99.9    p99.99    2xx    3xx    4xx    5xx Timeouts Errors\n");
      for (Map.Entry<String, Map<String, StatisticsSummary>> phaseEntry : stats.entrySet()) {
         sb.append(phaseEntry.getKey()).append(':').append('\n');
         for (Map.Entry<String, StatisticsSummary> sequenceEntry : phaseEntry.getValue().entrySet()) {
            StatisticsSummary summary = sequenceEntry.getValue();
            sb.append('\t').append(sequenceEntry.getKey()).append(": ");
            printSpaces(sb, longestSequenceName - sequenceEntry.getKey().length());
            sb.append(String.format("%8d ", summary.requestCount));
            sb.append(Util.prettyPrintNanos(summary.meanResponseTime));
            sb.append(' ');
            for (int i = 0; i < summary.percentileResponseTime.length; ++i) {
               sb.append(Util.prettyPrintNanos(summary.percentileResponseTime[i])).append(' ');
            }
            sb.append(String.format("%6d", summary.status_2xx))
                  .append(String.format(" %6d", summary.status_3xx))
                  .append(String.format(" %6d", summary.status_4xx))
                  .append(String.format(" %6d", summary.status_5xx))
                  .append(String.format(" %8d", summary.timeouts))
                  .append(String.format(" %6d", summary.status_other + summary.connectFailureCount + summary.resetCount));
            sb.append('\n');
         }
      }
      return sb.toString();
   }

   private void printSpaces(StringBuilder sb, int i) {
      for (;i > 0; --i) {
         sb.append(' ');
      }
   }


   private static String encode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }
}
