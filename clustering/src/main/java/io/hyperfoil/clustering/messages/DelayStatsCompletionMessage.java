package io.hyperfoil.clustering.messages;

public class DelayStatsCompletionMessage extends StatsMessage {
   public final int phaseId;
   public final long delay;

   public DelayStatsCompletionMessage(String address, String runId, int phaseId, long delay) {
      super(address, runId);
      this.phaseId = phaseId;
      this.delay = delay;
   }

   public static class Codec extends ObjectCodec<DelayStatsCompletionMessage> {
   }
}
