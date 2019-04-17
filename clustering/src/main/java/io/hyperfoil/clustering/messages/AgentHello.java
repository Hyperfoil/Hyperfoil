package io.hyperfoil.clustering.messages;

import java.io.Serializable;

import io.hyperfoil.util.Immutable;

public class AgentHello implements Serializable, Immutable {
   private String name;
   private String address;
   private String runId;

   public AgentHello(String name, String address, String runId) {
      this.name = name;
      this.address = address;
      this.runId = runId;
   }

   public String name() {
      return name;
   }

   public String address() {
      return address;
   }

   public String runId() {
      return runId;
   }

   public static class Codec extends ObjectCodec<AgentHello> {}
}
