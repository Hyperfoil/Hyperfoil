package io.sailrocket.core.builders;

public class PhaseForkBuilder implements Rewritable<PhaseForkBuilder> {
   final String name;
   final ScenarioBuilder scenario;
   double weight = 1;

   public PhaseForkBuilder(PhaseBuilder parent, String name) {
      this.name = name;
      this.scenario = new ScenarioBuilder(parent);
   }

   public PhaseForkBuilder weight(double weight) {
      this.weight = weight;
      return this;
   }

   public ScenarioBuilder scenario() {
      return scenario;
   }

   @Override
   public void readFrom(PhaseForkBuilder other) {
      this.weight = other.weight;
      this.scenario.readFrom(other.scenario);
   }
}
