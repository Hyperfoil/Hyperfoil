package io.hyperfoil.clustering;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.clustering.messages.ConnectionStatsMessage;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.core.util.LowHigh;
import io.vertx.core.eventbus.EventBus;

public class ConnectionStatsSender implements ConnectionStatsConsumer {
   private final EventBus eventBus;
   private final String address;
   private final String runId;
   private Map<String, Map<String, LowHigh>> stats = new HashMap<>();

   public ConnectionStatsSender(EventBus eb, String address, String runId) {
      this.eventBus = eb;
      this.address = address;
      this.runId = runId;
   }

   public void send() {
      eventBus.send(Feeds.STATS, new ConnectionStatsMessage(address, runId, System.currentTimeMillis(), stats));
      // the eventBus may process this asynchronously so we can't reuse the map
      stats = new HashMap<>();
   }

   @Override
   public void accept(String authority, String tag, int min, int max) {
      Map<String, LowHigh> byTag = stats.computeIfAbsent(authority, a -> new HashMap<>());
      LowHigh lowHigh = byTag.computeIfAbsent(tag, t -> new LowHigh());
      lowHigh.low += min;
      lowHigh.high += max;
   }
}
