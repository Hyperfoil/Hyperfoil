package io.sailrocket.distributed;

import java.util.HashMap;
import java.util.Map;

import io.sailrocket.core.api.PhaseInstance;

public class AgentInfo {
   final String address;
   Status status = Status.REGISTERED;
   Map<String, PhaseInstance.Status> phases = new HashMap<>();

   public AgentInfo(String address) {
      this.address = address;
   }

   public enum Status {
      REGISTERED,
      INITIALIZING,
      INITIALIZED,
      FAILED
   }
}
