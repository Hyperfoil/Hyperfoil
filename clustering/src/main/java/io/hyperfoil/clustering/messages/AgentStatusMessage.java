package io.hyperfoil.clustering.messages;

import java.io.Serializable;

public abstract class AgentStatusMessage implements Serializable {
   protected final String senderId;
   protected final String runId;

   public AgentStatusMessage(String senderId, String runId) {
      this.senderId = senderId;
      this.runId = runId;
   }

   public String senderId() {
      return senderId;
   }

   public String runId() {
      return runId;
   }
}
