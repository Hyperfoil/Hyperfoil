package io.hyperfoil.clustering.messages;

public class ErrorMessage extends AgentStatusMessage {
   private final Throwable error;

   public ErrorMessage(String senderId, String runId, Throwable error) {
      super(senderId, runId);
      this.error = error;
   }

   public Throwable error() {
      return error;
   }

   public static class Codec extends ObjectCodec<ErrorMessage> {}
}
