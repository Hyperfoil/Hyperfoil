package io.hyperfoil.http.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.hyperfoil.core.util.LowHigh;
import io.hyperfoil.http.config.ConnectionStrategy;

public class Http1xConnectionStatsTest extends AbstractConnectionStatsTest {

   private static final String HTTP_1x = "HTTP 1.x";
   private static final String IN_FLIGHT_REQUESTS = "in-flight requests";
   private static final String USED_CONNECTIONS = "used connections";

   @Test
   public void testSingleOkSharedHttp1x() {
      // startServer(ctx, false);
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSharedHttp1x() {
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSharedHttp1x() {
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(1);
      testSingle("/close", false);
   }

   @Test
   public void testSingleOkSessionPoolsHttp1x() {
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/ok", true);
   }

   @Test
   public void testSingleErrorSessionPoolsHttp1x() {
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/error", true);
   }

   @Test
   public void testSingleCloseSessionPoolsHttp1x() {
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(1);
      testSingle("/close", false);
   }

   @Test
   public void testSharedHttp1x() {
      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SHARED_POOL)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSharedHttp1xPipelining() {
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
   public void testSessionPoolsHttp1x() {
      final int connections = 3;
      http().connectionStrategy(ConnectionStrategy.SESSION_POOLS)
            .sharedConnections(connections);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(HTTP_1x).high).isEqualTo(connections);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isLessThanOrEqualTo(connections);
      assertThat(stats.get(USED_CONNECTIONS).high).isLessThanOrEqualTo(connections);
   }

   @Test
   public void testSessionPoolsHttp1xPipelining() {
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
   public void testNewHttp1x() {
      http().connectionStrategy(ConnectionStrategy.ALWAYS_NEW);

      Map<String, LowHigh> stats = testConcurrent(true);
      assertThat(stats.get(IN_FLIGHT_REQUESTS).high).isEqualTo(stats.get(USED_CONNECTIONS).high);
   }

   @Test
   public void testOnRequestHttp1x() {
      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST);

      testConcurrent(true);
      // Note: in-flight and used-connections stats can run out of sync because
      // other session can be executed after releasing connection and before resetting the session
   }

   @Test
   public void testOnRequestHttp1xPipelining() {
      http().connectionStrategy(ConnectionStrategy.OPEN_ON_REQUEST)
            .pipeliningLimit(5);

      testConcurrent(true);
      // Note: in-flight and used-connections stats can run out of sync because
      // other session can be executed after releasing connection and before resetting the session
   }
}
