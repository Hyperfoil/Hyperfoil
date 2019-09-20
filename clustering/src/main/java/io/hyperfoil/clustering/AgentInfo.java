package io.hyperfoil.clustering;

import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.deployment.DeployedAgent;
import io.hyperfoil.api.session.PhaseInstance;

class AgentInfo {
   final String name;
   final int id;
   String nodeId;
   String deploymentId;
   Status status = Status.STARTING;
   Map<String, PhaseInstance.Status> phases = new HashMap<>();
   DeployedAgent deployedAgent;

   public AgentInfo(String name, int id) {
      this.name = name;
      this.id = id;
   }

   public enum Status {
      STARTING,
      REGISTERED,
      INITIALIZING,
      INITIALIZED,
      STOPPED,
      FAILED
   }
}
