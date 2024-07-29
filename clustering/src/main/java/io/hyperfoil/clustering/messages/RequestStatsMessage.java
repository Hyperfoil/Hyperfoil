package io.hyperfoil.clustering.messages;

import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class RequestStatsMessage extends StatsMessage {
   public final int phaseId;
   public final int stepId;
   public final String metric;
   public final StatisticsSnapshot statistics;

   public RequestStatsMessage(String address, String runId, int phaseId, int stepId, String metric,
         StatisticsSnapshot statistics) {
      super(address, runId);
      this.phaseId = phaseId;
      this.stepId = stepId;
      this.metric = metric;
      this.statistics = statistics;
   }

   public static class Codec extends ObjectCodec<RequestStatsMessage> {
   }
}
