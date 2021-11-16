package io.hyperfoil.api.config;

public class PhaseForkBuilder {
   public final String name;
   public final ScenarioBuilder scenario;
   double weight = 1;

   public PhaseForkBuilder(PhaseBuilder<?> parent, String name) {
      this.name = name;
      this.scenario = new ScenarioBuilder(parent, this);
   }

   public PhaseForkBuilder weight(double weight) {
      this.weight = weight;
      return this;
   }

   public ScenarioBuilder scenario() {
      return scenario;
   }

   public void readFrom(PhaseForkBuilder other) {
      this.weight = other.weight;
      this.scenario.readFrom(other.scenario);
   }
}
