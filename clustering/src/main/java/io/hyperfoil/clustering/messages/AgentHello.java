package io.hyperfoil.clustering.messages;

import java.io.Serializable;

import io.hyperfoil.util.Immutable;

public class AgentHello implements Serializable, Immutable {
   private String name;
   private String address;

   public AgentHello(String name, String address) {
      this.name = name;
      this.address = address;
   }

   public String name() {
      return name;
   }

   public String address() {
      return address;
   }

   public static class Codec extends ObjectCodec<AgentHello> {}
}
