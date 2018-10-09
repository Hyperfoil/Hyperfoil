package io.sailrocket.clustering.util;

import java.io.Serializable;

import io.sailrocket.api.config.Simulation;

public class PhaseControlMessage implements Serializable {
   private final Command command;
   private final String phase;

   public PhaseControlMessage(Command command, Simulation simulation, String phase) {
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
