package io.hyperfoil.clustering.util;

import java.io.Serializable;
import java.util.Map;

public class SessionStatsMessage extends StatsMessage {
   public final long timestamp;
   public final Map<String, MinMax> sessionStats;

   public SessionStatsMessage(String address, String runId, long timestamp, Map<String, MinMax> sessionStats) {
      super(address, runId);
      this.timestamp = timestamp;
      this.sessionStats = sessionStats;
   }

   public static class MinMax implements Serializable {
      public int min;
      public int max;

      public MinMax(int min, int max) {
         this.min = min;
         this.max = max;
      }
   }

   public static class Codec extends ObjectCodec<SessionStatsMessage> {}
}
