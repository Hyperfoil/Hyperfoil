package io.hyperfoil.api.config;

public class PhaseReferenceDelay extends PhaseReference {
   public final long delay;

   public PhaseReferenceDelay(String phase, RelativeIteration iteration, String fork, long delay) {
      super(phase, iteration, fork);
      this.delay = delay;
   }
}
