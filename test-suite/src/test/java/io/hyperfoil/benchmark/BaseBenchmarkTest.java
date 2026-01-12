package io.hyperfoil.benchmark;

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.util.CountDown;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public abstract class BaseBenchmarkTest {
   protected Vertx vertx;
   protected HttpServer httpServer;

   @BeforeEach
   public void before(Vertx vertx, VertxTestContext ctx) {
      this.vertx = vertx;
      setupHttpServer(ctx, getRequestHandler());
   }

   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> req.response().end();
   }

   protected void setupHttpServer(VertxTestContext ctx, Handler<HttpServerRequest> handler) {
      httpServer = vertx.createHttpServer().requestHandler(handler).listen(0, "localhost", ctx.succeedingThenComplete());
   }

   protected String getBenchmarkPath(String name) {
      URL resource = getClass().getClassLoader().getResource(name);
      if (resource == null) {
         throw new AssertionError("Resource named: " + name + " not found");
      }
      File benchmark = new File(resource.getFile());
      return benchmark.getAbsolutePath();
   }

   public class TestStatistics implements StatisticsCollector.StatisticsConsumer {
      private final Map<String, Map<String, StatisticsSnapshot>> phaseStats = new HashMap<>();

      @Override
      public void accept(Phase phase, int stepId, String metric, StatisticsSnapshot snapshot, CountDown countDown) {
         Map<String, StatisticsSnapshot> stats = phaseStats.get(phase.name);
         if (stats == null) {
            stats = new HashMap<>();
            phaseStats.put(phase.name, stats);
         }
         StatisticsSnapshot metricValue = stats.get(metric);
         if (metricValue == null) {
            metricValue = new StatisticsSnapshot();
            stats.put(metric, metricValue);
         }
         metricValue.add(snapshot);
      }

      public Map<String, Map<String, StatisticsSnapshot>> stats() {
         return phaseStats;
      }
   }
}
