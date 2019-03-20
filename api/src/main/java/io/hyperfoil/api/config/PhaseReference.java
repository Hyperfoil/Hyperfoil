package io.hyperfoil.api.config;

public class PhaseReference {
   public final String phase;
   public final RelativeIteration iteration;
   public final String fork;

   public PhaseReference(String phase, RelativeIteration iteration, String fork) {
      this.phase = phase;
      this.iteration = iteration;
      this.fork = fork;
   }
}
