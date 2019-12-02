package io.hyperfoil.core.steps;

import java.util.concurrent.atomic.AtomicInteger;

import io.hyperfoil.api.config.Step;

public abstract class BaseStep implements Step {
   private static final AtomicInteger idCounter = new AtomicInteger();

   private final int id = idCounter.incrementAndGet();

   public int id() {
      return id;
   }
}
