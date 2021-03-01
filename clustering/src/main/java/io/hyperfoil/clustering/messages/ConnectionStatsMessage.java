package io.hyperfoil.clustering.messages;

import java.util.Map;

import io.hyperfoil.core.util.LowHigh;

public class ConnectionStatsMessage extends StatsMessage {
   public final long timestamp;
   public final Map<String, Map<String, LowHigh>> stats;

   public ConnectionStatsMessage(String address, String runId, long timestamp, Map<String, Map<String, LowHigh>> stats) {
      super(address, runId);
      this.timestamp = timestamp;
      this.stats = stats;
   }

   public static class Codec extends ObjectCodec<ConnectionStatsMessage> {}
}
