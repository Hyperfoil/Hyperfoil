package io.hyperfoil.core.steps;

import java.util.concurrent.atomic.AtomicInteger;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.Visitor;

public abstract class StatisticsStep implements Step {
   private static final AtomicInteger ID_COUNTER = new AtomicInteger();

   @Visitor.Ignore
   private final int id;

   public static int nextId() {
      return ID_COUNTER.getAndIncrement();
   }

   protected StatisticsStep(int id) {
      if (id < 0) {
         throw new IllegalArgumentException();
      }
      this.id = id;
   }

   public int id() {
      return id;
   }
}
