package io.hyperfoil.clustering.messages;

import java.io.Serializable;

import io.hyperfoil.api.config.Benchmark;

public class AgentControlMessage implements Serializable {
   private Command command;
   private int agentId;
   private Object param;

   public AgentControlMessage(Command command, int agentId, Object param) {
      this.command = command;
      this.agentId = agentId;
      this.param = param;
   }

   public Command command() {
      return command;
   }

   public Benchmark benchmark() {
      return (Benchmark) param;
   }

   public boolean includeInactive() {
      return (Boolean) param;
   }

   public int agentId() {
      return agentId;
   }

   public enum Command {
      INITIALIZE,
      STOP,
      LIST_SESSIONS,
      LIST_CONNECTIONS
   }

   public static class Codec extends ObjectCodec<AgentControlMessage> {
   }
}
