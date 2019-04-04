package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

public class RandomIntStep implements Step, ResourceUtilizer {
   private final Access var;
   private final int minInclusive;
   private final int maxInclusive;

   public RandomIntStep(String var, int minInclusive, int maxInclusive) {
      this.var = SessionFactory.access(var);
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
      var.setInt(session, r);
      return true;
   }

   @Override
   public void reserve(Session session) {
      var.declareInt(session);
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
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
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
