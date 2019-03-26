package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;

public abstract class BaseStepBuilder implements StepBuilder {
   private final BaseSequenceBuilder parent;

   protected BaseStepBuilder(BaseSequenceBuilder parent) {
      this.parent = parent;
      if (parent != null) {
         parent.stepBuilder(this);
      }
   }

   public BaseSequenceBuilder endStep() {
      return parent;
   }
}
