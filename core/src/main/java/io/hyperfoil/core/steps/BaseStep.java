package io.hyperfoil.core.steps;

import java.util.concurrent.atomic.AtomicInteger;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.function.SerializableSupplier;

public abstract class BaseStep implements Step {
   private static final AtomicInteger idCounter = new AtomicInteger();

   private final int id = idCounter.incrementAndGet();
   private final SerializableSupplier<Sequence> sequence;

   public BaseStep(SerializableSupplier<Sequence> sequence) {
      this.sequence = sequence;
   }

   public Sequence sequence() {
      return sequence.get();
   }

   public int id() {
      return id;
   }
}
