package io.hyperfoil.clustering.messages;

import java.util.Map;

import io.hyperfoil.core.util.LowHigh;

public class SessionStatsMessage extends StatsMessage {
   public final long timestamp;
   public final Map<String, LowHigh> sessionStats;

   public SessionStatsMessage(String address, String runId, long timestamp, Map<String, LowHigh> sessionStats) {
      super(address, runId);
      this.timestamp = timestamp;
      this.sessionStats = sessionStats;
   }

   public static class Codec extends ObjectCodec<SessionStatsMessage> {
   }
}
