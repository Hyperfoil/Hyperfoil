package io.sailrocket.clustering.util;

import java.io.Serializable;

import io.sailrocket.api.config.Simulation;
import io.sailrocket.util.Immutable;

public class AgentControlMessage implements Serializable, Immutable {
   private Command command;
   private String runId;
   private Simulation simulation;

   public AgentControlMessage(Command command, String runId, Simulation simulation) {
      this.command = command;
      this.runId = runId;
      this.simulation = simulation;
   }

   public Command command() {
      return command;
   }

   public String runId() {
      return runId;
   }

   public Simulation simulation() {
      return simulation;
   }

   public enum Command {
      INITIALIZE,
      RESET
   }

   public static class Codec extends ObjectCodec<AgentControlMessage> {}
}
