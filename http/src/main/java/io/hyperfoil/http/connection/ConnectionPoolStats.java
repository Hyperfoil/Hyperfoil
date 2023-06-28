package io.hyperfoil.http.connection;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.core.util.Watermarks;
import io.hyperfoil.http.api.HttpConnection;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

class ConnectionPoolStats {
   private static final Logger log = LogManager.getLogger(ConnectionPoolStats.class);
   protected final String authority;
   protected final Watermarks usedConnections = new Watermarks();
   protected final Watermarks inFlight = new Watermarks();
   protected final Watermarks blockedSessions = new Watermarks();
   protected final Map<String, Watermarks> typeStats = new HashMap<>();

   ConnectionPoolStats(String authority) {
      this.authority = authority;
   }

   public void incrementInFlight() {
      inFlight.incrementUsed();
   }

   public void decrementInFlight() {
      inFlight.decrementUsed();
   }

   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      consumer.accept(authority, "in-flight requests", inFlight.minUsed(), inFlight.maxUsed());
      inFlight.resetStats();
      consumer.accept(authority, "used connections", usedConnections.minUsed(), usedConnections.maxUsed());
      usedConnections.resetStats();
      consumer.accept(authority, "blocked sessions", blockedSessions.minUsed(), blockedSessions.maxUsed());
      blockedSessions.resetStats();
      for (var entry : typeStats.entrySet()) {
         int min = entry.getValue().minUsed();
         int max = entry.getValue().maxUsed();
         entry.getValue().resetStats();
         consumer.accept(authority, entry.getKey(), min, max);
      }
   }

   protected String tagConnection(HttpConnection connection) {
      switch (connection.version()) {
         case HTTP_1_0:
         case HTTP_1_1:
            return connection.isSecure() ? "TLS + HTTP 1.x" : "HTTP 1.x";
         case HTTP_2_0:
            return connection.isSecure() ? "TLS + HTTP 2" : "HTTP 2";
      }
      return "unknown";
   }

   protected void incrementTypeStats(HttpConnection conn) {
      typeStats.computeIfAbsent(tagConnection(conn), t -> new Watermarks()).incrementUsed();
   }
}
