package io.sailrocket.clustering.util;

import java.io.Serializable;

import io.sailrocket.api.statistics.StatisticsSnapshot;
import io.sailrocket.util.Copyable;

public class ReportMessage implements Serializable, Copyable {
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

   @Override
   public Copyable copy() {
      StatisticsSnapshot statisticsCopy = new StatisticsSnapshot();
      statistics.copyInto(statisticsCopy);
      return new ReportMessage(address, phase, sequence, statisticsCopy);
   }

   public static class Codec extends ObjectCodec<ReportMessage> {}
}
