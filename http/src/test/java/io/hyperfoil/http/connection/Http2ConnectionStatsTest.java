package io.hyperfoil.http.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.hyperfoil.core.util.LowHigh;
import io.hyperfoil.http.config.ConnectionStrategy;

public class Http2ConnectionStatsTest extends AbstractConnectionStatsTest {

   private static final String HTTP_2_TLS = "TLS + HTTP 2";
   private static final String BLOCKED_SESSIONS = "blocked sessions";
   private static final String IN_FLIGHT_REQUESTS = "in-flight requests";
   private static final String USED_CONNECTIONS = "used connections";

   @Override
   protected boolean useHttps() {
      return true;
   }

   @Test
   public void testSingleOkSharedHttp2() {
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSharedHttp2() {
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSharedHttp2() {
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/close", false);
   }

   @Test
   public void testSharedHttp2() {
      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      // Too many false positives
      //      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isBetween(connections + 1, 50);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testSharedHttp2NoErrors() {
      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(false);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      assertThat(stats.get(BLOCKED_SESSIONS).high).isEqualTo(0);
      // Too many false positives
      //      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isBetween(connections + 1, 50);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testSessionPoolsHttp2() {
      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_2_TLS).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isEqualTo(connections);
   }

   @Test
   public void testNewHttp2() {
      http().connectionStrategy(ConnectionStrategy.ALWAYS_NEW);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }

   @Test
   public void testOnRequestHttp2() {
      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }
}
