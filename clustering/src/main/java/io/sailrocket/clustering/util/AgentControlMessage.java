package io.sailrocket.clustering.util;

import java.io.Serializable;

import io.sailrocket.api.config.Simulation;
import io.sailrocket.util.Immutable;

public class AgentControlMessage implements Serializable, Immutable {
   private Command command;
   private String runId;
   private Object param;

   public AgentControlMessage(Command command, String runId, Object param) {
      this.command = command;
      this.runId = runId;
      this.param = param;
   }

   public Command command() {
      return command;
   }

   public String runId() {
      return runId;
   }

   public Simulation simulation() {
      return (Simulation) param;
   }

   public boolean includeInactive() {
      return (Boolean) param;
   }

   public enum Command {
      INITIALIZE,
      RESET,
      LIST_SESSIONS,
      LIST_CONNECTIONS
   }

   public static class Codec extends ObjectCodec<AgentControlMessage> {}
}
