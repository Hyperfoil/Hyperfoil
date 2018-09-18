package io.sailrocket.distributed.util;

import java.io.Serializable;
import java.util.Map;

import io.sailrocket.api.Report;
import io.sailrocket.api.StatisticsSnapshot;

public class ReportMessage implements Serializable {
   public final String address;
   public final String phase;
   public final String sequence;
   public final StatisticsSnapshot statistics;

   public ReportMessage(String address, String phase, String sequence, StatisticsSnapshot statistics) {
      this.address = address;
      this.phase = phase;
      this.sequence = sequence;
      this.statistics = statistics;
   }

   public static class Codec extends ObjectCodec<ReportMessage> {}
}
