package io.hyperfoil.core.util;

import java.util.function.BiConsumer;

public class DoubleIncrementBuilder {
   private final BiConsumer<Double, Double> consumer;
   private double base;
   private double increment;

   public DoubleIncrementBuilder(BiConsumer<Double, Double> consumer) {
      this.consumer = consumer;
   }

   public DoubleIncrementBuilder base(double base) {
      this.base = base;
      return this;
   }

   public DoubleIncrementBuilder increment(double increment) {
      this.increment = increment;
      return this;
   }

   public void apply() {
      consumer.accept(base, increment);
   }
}
