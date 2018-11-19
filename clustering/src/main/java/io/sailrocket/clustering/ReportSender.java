package io.sailrocket.clustering;

import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.api.statistics.StatisticsSnapshot;
import io.sailrocket.core.impl.statistics.StatisticsCollector;
import io.sailrocket.clustering.util.ReportMessage;
import io.vertx.core.eventbus.EventBus;

public class ReportSender extends StatisticsCollector {
   private final String address;
   private final String runId;
   private final EventBus eb;
   private final StatisticsConsumer sendReport = this::sendReport;

   public ReportSender(Simulation simulation, EventBus eb, String address, String runId) {
      super(simulation);
      this.eb = eb;
      this.address = address;
      this.runId = runId;
   }

   public void send() {
      visitStatistics(sendReport);
   }

   private boolean sendReport(Phase phase, Sequence sequence, StatisticsSnapshot statistics) {
      // On a clustered event bus the statistics snapshot is marshalled synchronously, so we can reset it in the caller
      // On a local event bus we enforce doing a copy (synchronously) by implementing copyable.
      eb.send(Feeds.STATS, new ReportMessage(address, runId, phase.name(), sequence.name(), statistics));
      return false;
   }
}
