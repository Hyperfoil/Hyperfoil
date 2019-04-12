package io.hyperfoil.clustering;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;
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

   public ReportSender(Benchmark benchmark, EventBus eb, String address, String runId) {
      super(benchmark);
      this.eb = eb;
      this.address = address;
      this.runId = runId;
   }

   public void send(CountDown completion) {
      visitStatistics(sendReport, completion);
   }

   private boolean sendReport(Phase phase, int stepId, String name, StatisticsSnapshot statistics, CountDown countDown) {
      if (statistics.histogram.getEndTimeStamp() >= statistics.histogram.getStartTimeStamp()) {
         log.debug("Sending stats for {}/{}, {} requests", stepId, name, statistics.requestCount);
         // On clustered eventbus, ObjectCodec is not called synchronously so we *must* do a copy here.
         // (on a local eventbus we'd have to do a copy in transform() anyway)
         StatisticsSnapshot copy = new StatisticsSnapshot();
         statistics.copyInto(copy);
         countDown.increment();
         eb.send(Feeds.STATS, new ReportMessage(address, runId, phase.id(), stepId, name, copy),
               reply -> countDown.countDown());
      }
      return false;
   }
}
