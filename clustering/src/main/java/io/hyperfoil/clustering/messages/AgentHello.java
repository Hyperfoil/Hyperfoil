package io.hyperfoil.clustering.messages;

import java.io.Serializable;

public class AgentHello implements Serializable {
   private final String name;
   private final String nodeId;
   private final String deploymentId;
   private final String runId;

   public AgentHello(String name, String nodeId, String deploymentId, String runId) {
      this.name = name;
      this.nodeId = nodeId;
      this.deploymentId = deploymentId;
      this.runId = runId;
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

   public String runId() {
      return runId;
   }

   public static class Codec extends ObjectCodec<AgentHello> {
   }
}
