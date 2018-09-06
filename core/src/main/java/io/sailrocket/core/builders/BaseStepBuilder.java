package io.sailrocket.core.builders;

public abstract class BaseStepBuilder implements StepBuilder {
   private final SequenceBuilder parent;

   protected BaseStepBuilder(SequenceBuilder parent) {
      this.parent = parent;
      parent.step(this);
   }

   public SequenceBuilder endStep() {
      return parent;
   }
}
