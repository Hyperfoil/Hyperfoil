package io.hyperfoil.http.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.util.LowHigh;
import io.hyperfoil.http.BaseHttpScenarioTest;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.statistics.HttpStats;
import io.hyperfoil.http.steps.HttpStepCatalog;

public abstract class AbstractConnectionStatsTest extends BaseHttpScenarioTest {

   private static final String HTTP_1x = "HTTP 1.x";
   private static final String IN_FLIGHT_REQUESTS = "in-flight requests";
   private static final String USED_CONNECTIONS = "used connections";

   @Override
   protected void initRouter() {
      router.route("/ok").handler(ctx -> vertx.setTimer(5, id -> ctx.response().end()));
      router.route("/error").handler(ctx -> vertx.setTimer(5, id -> ctx.response().setStatusCode(400).end()));
      router.route("/close").handler(ctx -> ctx.response().reset());
   }

   @Override
   protected int threads() {
      return 1;
   }

   protected HttpBuilder http() {
      return benchmarkBuilder.plugin(HttpPluginBuilder.class).http();
   }

   protected void testSingle(String path, boolean response) {
      AtomicReference<HttpConnectionPool> connectionPoolRef = new AtomicReference<>();
      benchmarkBuilder.addPhase("test").atOnce(1).duration(10).scenario()
            .initialSequence("test")
            .step(session -> {
               connectionPoolRef.set(HttpDestinationTable.get(session).getConnectionPoolByAuthority(null));
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
      assertThat(snapshot.connectionErrors).isEqualTo(response ? 0 : 1);

      connectionStats.stats
            .forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isLessThanOrEqualTo(lowHigh.high));
      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isGreaterThanOrEqualTo(0));
   }

   protected Map<String, LowHigh> testConcurrent(boolean errors) {
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
      assertThat(snapshot.responseCount).isEqualTo(snapshot.requestCount - snapshot.connectionErrors);
      assertThat(snapshot.connectionErrors).isEqualTo(snapshot.requestCount - http.status_2xx - http.status_4xx);
      // Too many false positives
      //      assertThat(http.status_2xx).isGreaterThan(30);
      if (errors) {
         //         assertThat(http.status_4xx).isGreaterThan(30);
      } else {
         assertThat(http.status_4xx).isEqualTo(0);
      }

      connectionStats.stats
            .forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isLessThanOrEqualTo(lowHigh.high));
      connectionStats.stats.forEach((tag, lowHigh) -> assertThat(lowHigh.low).describedAs(tag).isGreaterThanOrEqualTo(0));
      return connectionStats.stats;
   }

   protected static class TestConnectionStats implements ConnectionStatsConsumer {
      Map<String, LowHigh> stats = new HashMap<>();

      @Override
      public void accept(String authority, String tag, int min, int max) {
         // ignoring authority (we'll use only one)
         LowHigh prev = stats.putIfAbsent(tag, new LowHigh(min, max));
         assert prev == null;
      }
   }
}
