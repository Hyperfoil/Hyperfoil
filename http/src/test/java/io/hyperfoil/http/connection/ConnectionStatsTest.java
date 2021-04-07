package io.hyperfoil.http.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.util.LowHigh;
import io.hyperfoil.http.HttpScenarioTest;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.ConnectionStrategy;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.http.steps.HttpStepCatalog;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ConnectionStatsTest extends HttpScenarioTest {

   private static final String HTTP_1x = "HTTP 1.x";
   private static final String HTTP_2_TLS = "TLS + HTTP 2";
   private static final String BLOCKED_SESSIONS = "blocked sessions";
   private static final String IN_FLIGHT_REQUESTS = "in-flight requests";
   private static final String USED_CONNECTIONS = "used connections";

   @Override
   protected Future<Void> startServer(TestContext ctx, boolean tls, boolean compression) {
      // don't start the server
      return null;
   }

   private void startServer(TestContext ctx, boolean ssl) {
      Async async = ctx.async();
      super.startServer(ctx, ssl, false).onComplete(ctx.asyncAssertSuccess(nil -> async.complete()));
      async.await();
   }

   @Override
   protected void initRouter() {
      router.route("/ok").handler(ctx -> vertx.setTimer(5, id -> ctx.response().end()));
      router.route("/error").handler(ctx -> vertx.setTimer(5, id -> ctx.response().setStatusCode(400).end()));
      router.route("/close").handler(ctx -> ctx.response().close());
   }

   @Override
   protected int threads() {
      return 1;
   }

   private HttpBuilder http() {
      return benchmarkBuilder.plugin(HttpPluginBuilder.class).http();
   }

   @Test
   public void testSingleOkSharedHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSharedHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSharedHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/close", false);
   }

   @Test
   public void testSingleOkSessionPoolsHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSessionPoolsHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSessionPoolsHttp1x(TestContext ctx) {
      startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/close", false);
   }

   @Test
   public void testSingleOkSharedHttp2(TestContext ctx) {
      startServer(ctx, true);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSharedHttp2(TestContext ctx) {
      startServer(ctx, true);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSharedHttp2(TestContext ctx) {
      startServer(ctx, true);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/close", false);
   }


   @Test
   public void testSharedHttp1x(TestContext ctx) {
      startServer(ctx, false);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSharedHttp1xPipelining(TestContext ctx) {
      startServer(ctx, false);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections)
            .pipeliningLimit(5);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections * 5);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSessionPoolsHttp1x(TestContext ctx) {
      startServer(ctx, false);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSessionPoolsHttp1xPipelining(TestContext ctx) {
      startServer(ctx, false);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(connections)
            .pipeliningLimit(5);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSharedHttp2(TestContext ctx) {
      startServer(ctx, true);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isBetween(connections + 1, 50);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testSharedHttp2NoErrors(TestContext ctx) {
      startServer(ctx, true);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(false);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      assertThat(stats.get(BLOCKED_SESSIONS).high).isEqualTo(0);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isBetween(connections + 1, 50);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testSessionPoolsHttp2(TestContext ctx) {
      startServer(ctx, true);

      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testNewHttp1x(TestContext ctx) {
      log.info("START testNewHttp1x");
      startServer(ctx, false);

      http().connectionStrategy(ConnectionStrategy.ALWAYS_NEW);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }

   @Test
   public void testOnRequestHttp1x(TestContext ctx) {
      log.info("START testOnRequestHttp1x");
      startServer(ctx, false);

      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST);

      testConcurrent(true);
      // Note: in-flight and used-connections stats can run out of sync because
      // other session can be executed after releasing connection and before resetting the session
   }

   @Test
   public void testOnRequestHttp1xPipelining(TestContext ctx) {
      log.info("START testOnRequestHttp1xPipelining");
      startServer(ctx, false);

      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST)
            .pipeliningLimit(5);

      testConcurrent(true);
      // Note: in-flight and used-connections stats can run out of sync because
      // other session can be executed after releasing connection and before resetting the session
   }

   @Test
   public void testNewHttp2(TestContext ctx) {
      startServer(ctx, true);

      http().connectionStrategy(ConnectionStrategy.ALWAYS_NEW);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }

   @Test
   public void testOnRequestHttp2(TestContext ctx) {
      startServer(ctx, true);

      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }

   private ConnectionPoolStats testSingle(String path, boolean response) {
      AtomicReference<HttpConnectionPool> connectionPoolRef = new AtomicReference<>();
      benchmarkBuilder.addPhase("test").atOnce(1).duration(10).scenario()
            .initialSequence("test")
            .step(session -> {
               connectionPoolRef.set(HttpDestinationTable.get(session).getConnectionPool(null));
               return true;
            })
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET).path(path).endStep();

      TestStatistics requestStats = new TestStatistics();
      TestConnectionStats connectionStats = new TestConnectionStats();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmarkBuilder.build(), requestStats, null, connectionStats);
      runner.run();

      try {
         // Some connections might be released after all sessions stopped so we need to wait for that as well.
         connectionPoolRef.get().executor().awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         throw new IllegalStateException(e);
      }

      ConnectionPoolStats connectionPool;
      if (connectionPoolRef.get() instanceof SessionConnectionPool) {
         connectionPool = (ConnectionPoolStats) connectionPoolRef.get().clientPool().next();
      } else {
         connectionPool = (ConnectionPoolStats) connectionPoolRef.get();
      }

      assertThat(connectionPool.usedConnections.current()).isEqualTo(0);
      assertThat(connectionPool.inFlight.current()).isEqualTo(0);
      assertThat(connectionPool.blockedSessions.current()).isEqualTo(0);

      StatisticsSnapshot snapshot = requestStats.stats().get("test");
      assertThat(snapshot.requestCount).isEqualTo(1);
      assertThat(snapshot.responseCount).isEqualTo(response ? 1 : 0);
      assertThat(snapshot.resetCount).isEqualTo(response ? 0 : 1);

      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isLessThanOrEqualTo(lowHigh.high));
      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isGreaterThanOrEqualTo(0));
      return connectionPool;
   }

   private Map<String, LowHigh> testConcurrent(boolean errors) {
      //@formatter:off
      benchmarkBuilder.addPhase("test").constantRate(100).duration(2000).scenario()
            .initialSequence("test")
            .step(HttpStepCatalog.SC).randomItem()
               .toVar("path")
               .list()
                  .add("/ok", 1)
                  .add("/error", errors ? 1 : 0)
                  .add("/close", errors ? 1 : 0)
               .end().endStep()
            .step(HttpStepCatalog.SC).httpRequest(HttpMethod.GET)
               .path().fromVar("path").end().endStep();
      //@formatter:on

      TestStatistics requestStats = new TestStatistics();
      TestConnectionStats connectionStats = new TestConnectionStats();
      LocalSimulationRunner runner = new LocalSimulationRunner(benchmarkBuilder.build(), requestStats, null, connectionStats);
      runner.run();

      StatisticsSnapshot snapshot = requestStats.stats().get("test");
      HttpStats http = HttpStats.get(snapshot);
      assertThat(snapshot.requestCount).isGreaterThan(100);
      // When connection cancels requests from other sessions these are recorded as resets
      assertThat(snapshot.responseCount).isEqualTo(snapshot.requestCount - snapshot.resetCount);
      assertThat(snapshot.resetCount).isEqualTo(snapshot.requestCount - http.status_2xx - http.status_4xx);
      assertThat(http.status_2xx).isGreaterThan(30);
      if (errors) {
         assertThat(http.status_4xx).isGreaterThan(30);
      } else {
         assertThat(http.status_4xx).isEqualTo(0);
      }

      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isLessThanOrEqualTo(lowHigh.high));
      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isGreaterThanOrEqualTo(0));
      return connectionStats.stats;
   }

   private static class TestConnectionStats implements ConnectionStatsConsumer {
      Map<String, LowHigh> stats = new HashMap<>();

      @Override
      public void accept(String authority, String tag, int min, int max) {
         // ignoring authority (we'll use only one)
         LowHigh prev = stats.putIfAbsent(tag, new LowHigh(min, max));
         assert prev == null;
      }
   }
}
