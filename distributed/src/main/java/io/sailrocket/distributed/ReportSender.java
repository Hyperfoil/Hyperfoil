package io.sailrocket.distributed;

import io.sailrocket.api.config.Phase;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.config.Simulation;
import io.sailrocket.api.statistics.StatisticsSnapshot;
import io.sailrocket.core.impl.statistics.StatisticsCollector;
import io.sailrocket.distributed.util.ReportMessage;
import io.vertx.core.eventbus.EventBus;

public class ReportSender extends StatisticsCollector {
   private final String address;
   private final EventBus eb;
   private final StatisticsConsumer sendReport = this::sendReport;

   public ReportSender(Simulation simulation, EventBus eb, String address) {
      super(simulation, true);
      this.eb = eb;
      this.address = address;
   }

   public void send() {
      visitStatistics(sendReport);
   }

   private boolean sendReport(Phase phase, Sequence sequence, StatisticsSnapshot statistics) {
      // Here we assume that statistics will be serialized before next statistics collection kicks in and the statistics
      // are reset. There are probably no guarantees that this happens synchronously, though.
      eb.send(Feeds.STATS, new ReportMessage(address, phase.name(), sequence.name(), statistics));
      return false;
   }
}
