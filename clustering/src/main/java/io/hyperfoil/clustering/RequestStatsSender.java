package io.hyperfoil.clustering;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.clustering.messages.PhaseStatsCompleteMessage;
import io.hyperfoil.clustering.messages.RequestStatsMessage;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.core.util.CountDown;
import io.vertx.core.eventbus.EventBus;

public class RequestStatsSender extends StatisticsCollector {
   private static final Logger log = LogManager.getLogger(RequestStatsSender.class);

   private final String address;
   private final String runId;
   private final EventBus eb;
   private final StatisticsConsumer sendStats = this::sendStats;

   public RequestStatsSender(Benchmark benchmark, EventBus eb, String address, String runId) {
      super(benchmark);
      this.eb = eb;
      this.address = address;
      this.runId = runId;
   }

   public void send(CountDown completion) {
      visitStatistics(sendStats, completion);
   }

   private void sendStats(Phase phase, int stepId, String metric, StatisticsSnapshot statistics, CountDown countDown) {
      if (statistics.histogram.getEndTimeStamp() >= statistics.histogram.getStartTimeStamp()) {
         log.debug("Sending stats for {} {}/{}, id {}: {} requests, {} responses", phase.name(), stepId, metric,
               statistics.sampleId, statistics.requestCount, statistics.responseCount);
         // On clustered eventbus, ObjectCodec is not called synchronously so we *must* do a copy here.
         // (on a local eventbus we'd have to do a copy in transform() anyway)
         StatisticsSnapshot copy = statistics.clone();
         countDown.increment();
         eb.request(Feeds.STATS, new RequestStatsMessage(address, runId, phase.id(), stepId, metric, copy),
               reply -> countDown.countDown());
      }
   }

   public void sendPhaseComplete(Phase phase, CountDown countDown) {
      for (int phaseAndStepId : aggregated.keySet()) {
         if (phase != null && phase != phases[phaseAndStepId >> 16]) {
            continue;
         }

         countDown.increment();
         eb.request(Feeds.STATS, new RequestStatsMessage(address, runId, phaseAndStepId >> 16, -1, null, null),
               reply -> countDown.countDown());
      }
      if (phase == null) {
         // TODO: it would be better to not send this for those phases that are already complete
         for (Phase p : phases) {
            eb.request(Feeds.STATS, new PhaseStatsCompleteMessage(address, runId, p.name()));
         }
      } else {
         eb.request(Feeds.STATS, new PhaseStatsCompleteMessage(address, runId, phase.name()));
      }
   }
}
