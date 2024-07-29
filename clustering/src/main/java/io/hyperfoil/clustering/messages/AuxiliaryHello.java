package io.hyperfoil.clustering.messages;

import java.io.Serializable;

public class AuxiliaryHello implements Serializable {
   private final String name;
   private final String nodeId;
   private final String deploymentId;

   public AuxiliaryHello(String name, String nodeId, String deploymentId) {
      this.name = name;
      this.nodeId = nodeId;
      this.deploymentId = deploymentId;
   }

   public String name() {
      return name;
   }

   public String nodeId() {
      return nodeId;
   }

   public String deploymentId() {
      return deploymentId;
   }

   public static class Codec extends ObjectCodec<AuxiliaryHello> {
   }
}
