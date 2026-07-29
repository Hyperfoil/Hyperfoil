package io.hyperfoil.api.config;

public class Ergonomics {

   private boolean compensateInternalLatency = false;

   public boolean compensateInternalLatency() {
      return compensateInternalLatency;
   }

   /**
    * When enabled, the first HTTP request in a root sequence of an open model phase will use the session's
    * intended (scheduled) start time as the request start timestamp, compensating for internal coordinated omission.
    *
    * @param compensateInternalLatency Enable internal latency compensation?
    * @return Self.
    */
   public Ergonomics compensateInternalLatency(boolean compensateInternalLatency) {
      this.compensateInternalLatency = compensateInternalLatency;
      return this;
   }
}
