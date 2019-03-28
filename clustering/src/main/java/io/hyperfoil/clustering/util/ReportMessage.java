package io.hyperfoil.clustering.util;

import java.io.Serializable;

import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class ReportMessage implements Serializable {
   public final String address;
   public final int stepId;
   public final String statisticsName;
   public final StatisticsSnapshot statistics;
   public final String runId;

   public ReportMessage(String address, String runId, int stepId, String statisticsName, StatisticsSnapshot statistics) {
      this.address = address;
      this.runId = runId;
      this.stepId = stepId;
      this.statisticsName = statisticsName;
      this.statistics = statistics;
   }

   public static class Codec extends ObjectCodec<ReportMessage> {}
}
