package io.hyperfoil.clustering.messages;

import java.io.Serializable;

public class PhaseControlMessage implements Serializable {
   private final Command command;
   private final String phase;

   public PhaseControlMessage(Command command, String phase) {
      this.command = command;
      this.phase = phase;
   }

   public Command command() {
      return command;
   }

   public String phase() {
      return phase;
   }

   public enum Command {
      RUN,
      FINISH,
      TRY_TERMINATE,
      TERMINATE
   }

   public static class Codec extends ObjectCodec<PhaseControlMessage> {}
}
