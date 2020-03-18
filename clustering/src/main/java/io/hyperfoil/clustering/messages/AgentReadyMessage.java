package io.hyperfoil.clustering.messages;

public class AgentReadyMessage extends AgentStatusMessage {
   public AgentReadyMessage(String senderId, String runId) {
      super(senderId, runId);
   }

   public static class Codec extends ObjectCodec<AgentReadyMessage> {}
}
