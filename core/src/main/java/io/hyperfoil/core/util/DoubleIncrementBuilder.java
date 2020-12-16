package io.hyperfoil.core.util;

import java.util.function.BiConsumer;

public class DoubleIncrementBuilder {
   private final BiConsumer<Double, Double> consumer;
   private double base;
   private double increment;

   public DoubleIncrementBuilder(BiConsumer<Double, Double> consumer) {
      this.consumer = consumer;
   }

   /**
    * Base value used for first iteration.
    *
    * @param base Value.
    * @return Self.
    */
   public DoubleIncrementBuilder base(double base) {
      this.base = base;
      return this;
   }

   /**
    * Value by which the base value is incremented for each (but the very first) iteration.
    *
    * @param increment Increment value.
    * @return Self.
    */
   public DoubleIncrementBuilder increment(double increment) {
      this.increment = increment;
      return this;
   }

   public void apply() {
      consumer.accept(base, increment);
   }
}
