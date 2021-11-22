package io.hyperfoil.clustering.messages;

import java.io.Serializable;
import java.util.Map;

import io.hyperfoil.api.session.GlobalData;

public class PhaseControlMessage implements Serializable {
   private final Command command;
   private final String phase;
   private final Map<String, GlobalData.Element> globalData;

   public PhaseControlMessage(Command command, String phase, Map<String, GlobalData.Element> globalData) {
      this.command = command;
      this.phase = phase;
      this.globalData = globalData;
   }

   public Command command() {
      return command;
   }

   public String phase() {
      return phase;
   }

   public Map<String, GlobalData.Element> globalData() {
      return globalData;
   }

   public enum Command {
      RUN,
      FINISH,
      TRY_TERMINATE,
      TERMINATE
   }

   public static class Codec extends ObjectCodec<PhaseControlMessage> {}
}
