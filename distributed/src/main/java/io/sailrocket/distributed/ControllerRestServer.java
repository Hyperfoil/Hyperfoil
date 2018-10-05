package io.sailrocket.distributed;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.parser.BenchmarkParser;
import io.sailrocket.core.parser.ConfigurationParserException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class ControllerRestServer {
   private static final Logger log = LoggerFactory.getLogger(ControllerRestServer.class);
   private static final String MIME_TYPE_SERIALIZED = "application/java-serialized-object";
   private static final Set<String> MIME_TYPE_YAML = new HashSet<>(
         Arrays.asList("text/vnd.yaml", "text/yaml", "text/x-yaml", "application/x-yaml"));

   private final String baseURL = "http://localhost:8090";
   private final AgentControllerVerticle controller;
   private final HttpServer httpServer;
   private Map<String, Benchmark> benchmarks = new HashMap<>();

   public ControllerRestServer(AgentControllerVerticle controller) {
      this.controller = controller;
      //create http api for controlling and monitoring agent
      Router router = Router.router(controller.getVertx());

      router.route().handler(BodyHandler.create());
      router.post("/benchmark").handler(this::handlePostBenchmark);
      router.get("/benchmark").handler(this::handleListBenchmarks);
      router.get("/benchmark/:benchmarkname").handler(this::handleGetBenchmark);
      router.get("/benchmark/:benchmarkname/start").handler(this::handleBenchmarkStart);
      router.get("/agents").handler(this::handleGetAgentCount);
      router.get("/run").handler(this::handleListRuns);
      router.get("/run/:runid").handler(this::handleGetRun);
      router.get("/run/:runid/kill").handler(this::handleRunKill);

      httpServer = controller.getVertx().createHttpServer().requestHandler(router::accept).listen(8090);
   }

   public void stop(Future<Void> stopFuture) {
      httpServer.close(result -> stopFuture.complete());
   }

   private void handlePostBenchmark(RoutingContext routingContext) {
      String contentType = routingContext.request().getHeader(HttpHeaders.CONTENT_TYPE).trim();
      Charset charset = StandardCharsets.UTF_8;
      int semicolonIndex = contentType.indexOf(';');
      if (semicolonIndex >= 0) {
         String tmp = contentType.substring(semicolonIndex + 1).trim();
         if (tmp.startsWith("charset=")) {
            charset = Charset.forName(tmp.substring(8));
         }
         contentType = contentType.substring(0, semicolonIndex).trim();
      }

      try {
         Benchmark benchmark;
         if (contentType.equals(MIME_TYPE_SERIALIZED)) {
            byte[] bytes = routingContext.getBody().getBytes();
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
               benchmark = (Benchmark) input.readObject();
            }
         } else if (MIME_TYPE_YAML.contains(contentType)) {
            String source = routingContext.getBodyAsString(charset.name());
            benchmark = BenchmarkParser.instance().buildBenchmark(source);
         } else {
            routingContext.response().setStatusCode(406).setStatusMessage("Unsupported Content-Type.");
            return;
         }

         benchmarks.put(benchmark.name(), benchmark);
         routingContext.response().setStatusCode(204)
               .putHeader(HttpHeaders.LOCATION, baseURL + "/benchmark/" + benchmark.name()).end();

      } catch (IOException | ClassNotFoundException | ClassCastException | ConfigurationParserException e) {
         log.error("Failed to read benchmark", e);
         routingContext.response().setStatusCode(400).end("Cannot read benchmark.");
      }
   }

   private void handleListBenchmarks(RoutingContext routingContext) {
      JsonArray array = new JsonArray();
      benchmarks.keySet().forEach(array::add);
      routingContext.response().setStatusCode(200).end(array.toBuffer());
   }

   private void handleGetBenchmark(RoutingContext routingContext) {
      String name = routingContext.pathParam("benchmarkname");
      Benchmark benchmark = benchmarks.get(name);
      if (benchmark == null) {
         routingContext.response().setStatusCode(404).setStatusMessage("No benchmark '" + name + "'").end();
         return;
      }

      String acceptHeader = routingContext.request().getHeader(HttpHeaders.ACCEPT);
      int semicolonIndex = acceptHeader.indexOf(';');
      if (semicolonIndex >= 0) {
         acceptHeader = acceptHeader.substring(0, semicolonIndex).trim();
      }
      if (acceptHeader.equals(MIME_TYPE_SERIALIZED)) {
         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
         try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            outputStream.writeObject(benchmark);
         } catch (IOException e) {
            routingContext.response().setStatusCode(500).end("Error encoding benchmark.");
            return;
         }
         routingContext.response().setStatusCode(200)
               .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_SERIALIZED)
               .end(Buffer.buffer(byteArrayOutputStream.toByteArray()));
      } else if (MIME_TYPE_YAML.contains(acceptHeader)) {
         if (benchmark.source() == null) {
            routingContext.response().setStatusCode(406).setStatusMessage("Benchmark does not preserve the original source.");
         } else {
            routingContext.response().setStatusCode(200)
                  .putHeader(HttpHeaders.CONTENT_TYPE, "text/vnd.yaml; charset=UTF-8")
                  .end(benchmark.source());
         }
      } else {
         routingContext.response().setStatusCode(406).setStatusMessage("Unsupported type in Accept.").end();
      }
   }

   private void handleBenchmarkStart(RoutingContext routingContext) {
      String benchmarkName = routingContext.pathParam("benchmarkname");
      Benchmark benchmark = benchmarks.get(benchmarkName);
      if (benchmark != null) {
         String runId = controller.startBenchmark(benchmark);
         routingContext.response().setStatusCode(202).
               putHeader(HttpHeaders.LOCATION, baseURL + "/run/" + runId).end("Initializing agents...");
      } else {
         //benchmark has not been defined yet
         String msg = "Benchmark not found";
         routingContext.response().setStatusCode(500).end(msg);
      }
   }

   private void handleGetAgentCount(RoutingContext routingContext) {
      routingContext.response().end(Integer.toString(controller.agents.size()));
   }

   private void handleListRuns(RoutingContext routingContext) {
      JsonArray array = new JsonArray();
      controller.runs().stream().map(run -> run.id).forEach(array::add);
      routingContext.response().setStatusCode(200).end(array.toBuffer());
   }

   private void handleGetRun(RoutingContext routingContext) {
      JsonObject body = new JsonObject();
      Run run = controller.run(routingContext.pathParam("runid"));
      if (run == null) {
         routingContext.response().setStatusCode(404).end();
         return;
      }
      body.put("runId", run.id);
      SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.S");
      if (run.benchmark != null) {
         body.put("benchmark", run.benchmark.name());
      }
      if (run.startTime > Long.MIN_VALUE) {
         body.put("started", simpleDateFormat.format(new Date(run.startTime)));
      }
      if (run.terminateTime > Long.MIN_VALUE) {
         body.put("terminated", simpleDateFormat.format(new Date(run.terminateTime)));
      }
      JsonArray jsonPhases = new JsonArray();
      body.put("phases", jsonPhases);
      for (ControllerPhase phase : run.phases.values()) {
         JsonObject jsonPhase = new JsonObject();
         jsonPhases.add(jsonPhase);
         jsonPhase.put("name", phase.definition().name);
         jsonPhase.put("status", phase.status());
         if (phase.absoluteStartTime() > Long.MIN_VALUE) {
            jsonPhase.put("started", simpleDateFormat.format(new Date(phase.absoluteStartTime())));
         }
      }
      JsonArray jsonAgents = new JsonArray();
      body.put("agents", jsonAgents);
      for (AgentInfo agent : controller.agents.values()) {
         JsonObject jsonAgent = new JsonObject();
         jsonAgents.add(jsonAgent);
         jsonAgent.put("address", agent.address);
         jsonAgent.put("status", agent.status);
      }
      String status = body.encodePrettily();
      routingContext.response().end(status);
   }

   private void handleRunKill(RoutingContext routingContext) {
      if (controller.kill(routingContext.pathParam("runid"))) {
         routingContext.response().setStatusCode(202).end();
      } else {
         routingContext.response().setStatusCode(404).end();
      }
   }
}
