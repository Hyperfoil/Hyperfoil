package io.hyperfoil.clustering.util;

import java.io.Serializable;

import io.hyperfoil.api.statistics.StatisticsSnapshot;

public class ReportMessage implements Serializable {
   public final String address;
   public final String phase;
   public final String sequence;
   public final StatisticsSnapshot statistics;
   public final String runId;

   public ReportMessage(String address, String runId, String phase, String sequence, StatisticsSnapshot statistics) {
      this.address = address;
      this.runId = runId;
      this.phase = phase;
      this.sequence = sequence;
      this.statistics = statistics;
   }

   public static class Codec extends ObjectCodec<ReportMessage> {}
}
