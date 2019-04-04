package io.hyperfoil.clustering;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.clustering.util.SessionStatsMessage;
import io.hyperfoil.core.impl.SessionStatsConsumer;
import io.vertx.core.eventbus.EventBus;

public class SessionStatsSender implements SessionStatsConsumer {
   private final String address;
   private final String runId;
   private final EventBus eb;
   private Map<String, SessionStatsMessage.MinMax> sessionStats;

   public SessionStatsSender(EventBus eb, String address, String runId) {
      this.address = address;
      this.runId = runId;
      this.eb = eb;
   }

   public void send() {
      if (sessionStats != null) {
         eb.send(Feeds.STATS, new SessionStatsMessage(address, runId, System.currentTimeMillis(), sessionStats));
         sessionStats = null;
      }
   }

   @Override
   public void accept(String phase, int minSessions, int maxSessions) {
      if (sessionStats == null) {
         sessionStats = new HashMap<>();
      }
      sessionStats.put(phase, new SessionStatsMessage.MinMax(minSessions, maxSessions));
   }
}
