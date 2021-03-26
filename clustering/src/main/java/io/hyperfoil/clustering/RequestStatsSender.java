package io.hyperfoil.clustering;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.util.CountDown;
import io.hyperfoil.core.impl.statistics.StatisticsCollector;
import io.hyperfoil.clustering.messages.RequestStatsMessage;
import io.vertx.core.eventbus.EventBus;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
               statistics.sequenceId, statistics.requestCount, statistics.responseCount);
         // On clustered eventbus, ObjectCodec is not called synchronously so we *must* do a copy here.
         // (on a local eventbus we'd have to do a copy in transform() anyway)
         StatisticsSnapshot copy = new StatisticsSnapshot();
         statistics.copyInto(copy);
         countDown.increment();
         eb.request(Feeds.STATS, new RequestStatsMessage(address, runId, phase.id(), false, stepId, metric, copy),
               reply -> countDown.countDown());
      }
   }

   public void sendPhaseComplete(Phase phase, CountDown countDown) {
      for (int phaseAndStepId : aggregated.keySet()) {
         if (phase != null && phase != phases[phaseAndStepId >> 16]) {
            continue;
         }

         countDown.increment();
         eb.request(Feeds.STATS, new RequestStatsMessage(address, runId, phaseAndStepId >> 16, true, -1, null, null),
               reply -> countDown.countDown());
      }
   }
}
