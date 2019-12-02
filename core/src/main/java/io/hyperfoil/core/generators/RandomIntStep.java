package io.hyperfoil.core.generators;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class RandomIntStep implements Step, ResourceUtilizer {
   private final Access toVar;
   private final int minInclusive;
   private final int maxInclusive;

   public RandomIntStep(String toVar, int minInclusive, int maxInclusive) {
      this.toVar = SessionFactory.access(toVar);
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
      toVar.setInt(session, r);
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareInt(session);
   }

   /**
    * Stores random (linearly distributed) integer into session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomInt")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String toVar;
      private int min = 0;
      private int max = Integer.MAX_VALUE;

      /**
       * @param rangeToVar Use format `var <- min .. max`
       * @return Self.
       */
      @Override
      public Builder init(String rangeToVar) {
         if (rangeToVar == null) {
            return this;
         }
         int arrowIndex = rangeToVar.indexOf("<-");
         int dotdotIndex = rangeToVar.indexOf("..");
         if (arrowIndex < 0 || dotdotIndex < arrowIndex) {
            throw new BenchmarkDefinitionException("Expecting format var <- min .. max");
         }
         toVar = rangeToVar.substring(0, arrowIndex).trim();
         min = Integer.parseInt(rangeToVar.substring(arrowIndex + 2, dotdotIndex).trim());
         max = Integer.parseInt(rangeToVar.substring(dotdotIndex + 2).trim());
         return this;
      }

      /**
       * Variable name to store the result.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      /**
       * Lowest possible value (inclusive)
       *
       * @param min Min value.
       * @return Self.
       */
      public Builder min(int min) {
         this.min = min;
         return this;
      }

      /**
       * Highest possible value (inclusive)
       *
       * @param max Max value.
       * @return Self.
       */
      public Builder max(int max) {
         this.max = max;
         return this;
      }

      @Override
      public List<Step> build() {
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing target var.");
         }
         if (min >= max) {
            throw new BenchmarkDefinitionException("min must be less than max");
         }
         return Collections.singletonList(new RandomIntStep(toVar, min, max));
      }
   }
}
