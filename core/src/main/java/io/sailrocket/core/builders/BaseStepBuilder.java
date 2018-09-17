package io.sailrocket.core.builders;

public abstract class BaseStepBuilder implements StepBuilder {
   protected final BaseSequenceBuilder parent;

   protected BaseStepBuilder(BaseSequenceBuilder parent) {
      this.parent = parent;
      parent.step(this);
   }

   public BaseSequenceBuilder endStep() {
      return parent;
   }
}
