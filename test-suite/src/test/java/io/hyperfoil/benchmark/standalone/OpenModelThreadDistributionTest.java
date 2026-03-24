package io.hyperfoil.benchmark.standalone;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

/**
 * Reproducer for <a href="https://github.com/Hyperfoil/Hyperfoil/issues/627">#627</a>:
 * Open models risk running single-threaded when the SUT is slow.
 * <p>
 * Uses a constantRate phase with a high rate against a slow server and a small maxSessions
 * to force throttling and catch-up. During catch-up, {@code OpenModelPhase.notifyFinished()}
 * immediately restarts finishing sessions on their original executor, causing all catch-up
 * sessions to cluster on a single event loop.
 * <p>
 * The test tracks which Hyperfoil event loop thread each session runs on and asserts that
 * no single thread handles a disproportionate share of requests.
 */
@Tag("io.hyperfoil.test.Benchmark")
public class OpenModelThreadDistributionTest extends BaseBenchmarkTest {

   private static final int THREADS = 4;
   private static final int SERVER_DELAY_MS = 50;
   private static final double MAX_THREAD_RATIO = 0.60;

   private final Map<String, LongAdder> requestsPerThread = new ConcurrentHashMap<>();

   @Override
   protected Handler<HttpServerRequest> getRequestHandler() {
      return req -> {
         // Track which Hyperfoil client event loop thread sent this request.
         // Netty writes happen on the event loop that owns the connection,
         // which is the same event loop the session is affined to.
         String threadName = Thread.currentThread().getName();
         requestsPerThread.computeIfAbsent(threadName, k -> new LongAdder()).increment();
         // Simulate a slow server to exhaust the session pool and trigger catch-up
         vertx.setTimer(SERVER_DELAY_MS, id -> req.response().end("ok"));
      };
   }

   @Test
   public void testSessionsDistributedAcrossThreadsDuringCatchUp() {
      // High rate + small maxSessions + slow server = guaranteed throttling and catch-up
      int usersPerSec = 200;
      int maxSessions = 20;
      int durationMs = 5000;

      // @formatter:off
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name("open-model-thread-distribution")
            .addPlugin(HttpPluginBuilder::new).http()
               .host("localhost").port(httpServer.actualPort())
               .sharedConnections(50)
            .endHttp().endPlugin()
            .threads(THREADS);

      builder.addPhase("test").constantRate(usersPerSec)
            .duration(durationMs)
            .maxSessions(maxSessions)
            .scenario()
               .initialSequence("request")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .path("/")
                     .timeout("60s")
                  .endStep()
               .endSequence();
      // @formatter:on

      Benchmark benchmark = builder.build();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmark);
      runner.run();

      long totalRequests = requestsPerThread.values().stream().mapToLong(LongAdder::sum).sum();
      System.out.println("Thread distribution (" + totalRequests + " total requests across " + THREADS + " threads):");
      for (var entry : requestsPerThread.entrySet()) {
         double ratio = (double) entry.getValue().sum() / totalRequests;
         System.out.printf("  %s: %d requests (%.1f%%)%n", entry.getKey(), entry.getValue().sum(), ratio * 100);
         assertTrue(ratio < MAX_THREAD_RATIO,
               String.format("Thread %s handled %.1f%% of requests (expected < %.0f%%). " +
                     "Catch-up sessions are clustering on a single event loop (issue #627).",
                     entry.getKey(), ratio * 100, MAX_THREAD_RATIO * 100));
      }
   }
}
