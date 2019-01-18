package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.function.SerializableSupplier;

public abstract class BaseStep implements Step {
   private final SerializableSupplier<Sequence> sequence;

   public BaseStep(SerializableSupplier<Sequence> sequence) {
      this.sequence = sequence;
   }

   public Sequence sequence() {
      return sequence.get();
   }
}
