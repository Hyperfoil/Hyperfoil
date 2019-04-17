package io.hyperfoil.clustering.messages;

import java.io.Serializable;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.util.Immutable;

public class AgentControlMessage implements Serializable, Immutable {
   private Command command;
   private Object param;

   public AgentControlMessage(Command command, Object param) {
      this.command = command;
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

   public enum Command {
      INITIALIZE,
      STOP,
      LIST_SESSIONS,
      LIST_CONNECTIONS
   }

   public static class Codec extends ObjectCodec<AgentControlMessage> {}
}
