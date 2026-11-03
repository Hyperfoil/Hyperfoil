package io.hyperfoil.clustering;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.hyperfoil.clustering.messages.SessionStatsMessage;
import io.hyperfoil.core.impl.SessionStatsConsumer;
import io.hyperfoil.core.util.LowHigh;
import io.vertx.core.eventbus.EventBus;

public class SessionStatsSender implements SessionStatsConsumer {
   private final String address;
   private final String runId;
   private final EventBus eb;
   private final BiConsumer<String, Throwable> abort;
   private Map<String, LowHigh> sessionStats;

   public SessionStatsSender(EventBus eb, String address, String runId, BiConsumer<String, Throwable> abort) {
      this.address = address;
      this.runId = runId;
      this.eb = eb;
      this.abort = abort;
   }

   public void send() {
      if (sessionStats != null) {
         EventBusSend.send(eb, Feeds.STATS, new SessionStatsMessage(address, runId, System.currentTimeMillis(), sessionStats),
               e -> abort.accept("session statistics", e));
         sessionStats = null;
      }
   }

   @Override
   public void accept(String phase, int minSessions, int maxSessions) {
      if (sessionStats == null) {
         sessionStats = new HashMap<>();
      }
      sessionStats.put(phase, new LowHigh(minSessions, maxSessions));
   }
}
