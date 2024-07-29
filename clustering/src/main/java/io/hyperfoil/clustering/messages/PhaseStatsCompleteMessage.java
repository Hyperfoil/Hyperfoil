package io.hyperfoil.clustering.messages;

import java.util.Objects;

public class PhaseStatsCompleteMessage extends StatsMessage {
   public final String phase;

   public PhaseStatsCompleteMessage(String address, String runId, String phase) {
      super(address, runId);
      this.phase = Objects.requireNonNull(phase);
   }

   public static class Codec extends ObjectCodec<PhaseStatsCompleteMessage> {
   }
}
