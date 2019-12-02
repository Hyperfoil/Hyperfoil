package io.hyperfoil.core.builders;

import java.util.Objects;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;

public abstract class BaseStepBuilder<T extends BaseStepBuilder<T>> implements StepBuilder<T> {
   private BaseSequenceBuilder parent;

   public T addTo(BaseSequenceBuilder parent) {
      if (this.parent != null) {
         throw new UnsupportedOperationException("Cannot add builder " + getClass().getName() + " to another sequence!");
      }
      parent.stepBuilder(this);
      this.parent = Objects.requireNonNull(parent);
      setLocator(parent.createLocator());
      @SuppressWarnings("unchecked")
      T self = (T) this;
      return self;
   }

   public BaseSequenceBuilder endStep() {
      if (parent == null) {
         throw new UnsupportedOperationException("Sequence for " + getClass().getName() + " was not set.");
      }
      return parent;
   }
}
