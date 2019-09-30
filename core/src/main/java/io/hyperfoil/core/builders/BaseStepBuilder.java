package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;

public abstract class BaseStepBuilder implements StepBuilder {
   private final BaseSequenceBuilder parent;

   /**
    * This constructor is used when the step is loaded as service; it must be added
    * to the parent later and can't be used through the fluent syntax ({@link #endStep()} returns null).
    */
   protected BaseStepBuilder() {
      parent = null;
   }

   protected BaseStepBuilder(BaseSequenceBuilder parent) {
      this.parent = parent;
      if (parent != null) {
         parent.stepBuilder(this);
      }
   }

   @Override
   public BaseSequenceBuilder endStep() {
      return parent;
   }
}
