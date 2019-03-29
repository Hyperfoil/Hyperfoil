package io.hyperfoil.clustering.util;

import java.io.Serializable;

public abstract class StatsMessage implements Serializable {
   public final String runId;
   public final String address;

   public StatsMessage(String address, String runId) {
      this.runId = runId;
      this.address = address;
   }
}
