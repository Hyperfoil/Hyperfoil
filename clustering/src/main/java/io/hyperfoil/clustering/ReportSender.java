package io.hyperfoil.clustering;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Simulation;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.clustering.util.ReportMessage;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ReportSender extends StatisticsCollector {
   private static final Logger log = LoggerFactory.getLogger(ReportSender.class);

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
      if (statistics.histogram.getEndTimeStamp() >= statistics.histogram.getStartTimeStamp()) {
         log.debug("Sending stats for {}/{}, {} requests", phase.name(), sequence.name(), statistics.requestCount);
         // On clustered eventbus, ObjectCodec is not called synchronously so we *must* do a copy here.
         // (on a local eventbus we'd have to do a copy in transform() anyway)
         StatisticsSnapshot copy = new StatisticsSnapshot();
         statistics.copyInto(copy);
         eb.send(Feeds.STATS, new ReportMessage(address, runId, phase.name(), sequence.name(), copy));
      }
      return false;
   }
}
