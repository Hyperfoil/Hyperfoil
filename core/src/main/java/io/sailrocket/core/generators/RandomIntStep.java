package io.sailrocket.core.generators;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;

public class RandomIntStep implements Step {
   private final String var;
   private final int minInclusive;
   private final int maxInclusive;

   public RandomIntStep(String var, int minInclusive, int maxInclusive) {
      this.var = var;
      this.minInclusive = minInclusive;
      this.maxInclusive = maxInclusive;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int r;
      if (maxInclusive == Integer.MAX_VALUE) {
         if (minInclusive == Integer.MIN_VALUE) {
            r = random.nextInt();
         } else {
            r = random.nextInt(minInclusive - 1, maxInclusive) + 1;
         }
      } else {
         r = random.nextInt(minInclusive, maxInclusive + 1);
      }
      session.setInt(var, r);
      return true;
   }

   public static class Builder extends BaseStepBuilder {
      private String var;
      private int min = 0;
      private int max = Integer.MAX_VALUE;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder min(int min) {
         this.min = min;
         return this;
      }

      public Builder max(int max) {
         this.max = max;
         return this;
      }

      @Override
      public List<Step> build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Missing target var.");
         }
         if (min >= max) {
            throw new BenchmarkDefinitionException("min must be less than max");
         }
         return Collections.singletonList(new RandomIntStep(var, min, max));
      }
   }
}
