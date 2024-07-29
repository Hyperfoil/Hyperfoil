package io.hyperfoil.clustering.messages;

public class ErrorMessage extends AgentStatusMessage {
   private final Throwable error;
   private final boolean fatal;

   public ErrorMessage(String senderId, String runId, Throwable error, boolean fatal) {
      super(senderId, runId);
      this.error = error;
      this.fatal = fatal;
   }

   public Throwable error() {
      return error;
   }

   public boolean isFatal() {
      return fatal;
   }

   public static class Codec extends ObjectCodec<ErrorMessage> {
   }
}
