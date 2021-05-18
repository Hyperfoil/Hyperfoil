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
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

public class RandomIntStep implements Step {
   private final IntAccess toVar;
   private final SerializableToIntFunction<Session> minInclusive;
   private final SerializableToIntFunction<Session> maxInclusive;

   public RandomIntStep(IntAccess toVar, SerializableToIntFunction<Session> minInclusive, SerializableToIntFunction<Session> maxInclusive) {
      this.toVar = toVar;
      this.minInclusive = minInclusive;
      this.maxInclusive = maxInclusive;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int r;
      int minInclusive = this.minInclusive.applyAsInt(session);
      int maxInclusive = this.maxInclusive.applyAsInt(session);
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

   /**
    * Stores random (linearly distributed) integer into session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomInt")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String toVar;
      private IntValueProviderBuilder<Builder> min = new IntValueProviderBuilder<>(this, 0);
      private IntValueProviderBuilder<Builder> max = new IntValueProviderBuilder<>(this, Integer.MAX_VALUE);

      /**
       * @param rangeToVar Use format `var &lt;- min .. max`
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
         min.value(Integer.parseInt(rangeToVar.substring(arrowIndex + 2, dotdotIndex).trim()));
         max.value(Integer.parseInt(rangeToVar.substring(dotdotIndex + 2).trim()));
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
       * Lowest possible value (inclusive). Default is 0.
       *
       * @return Self.
       */
      public IntValueProviderBuilder<Builder> min() {
         return min;
      }

      /**
       * Highest possible value (inclusive). Default is Integer.MAX_VALUE.
       *
       * @return Self.
       */
      public IntValueProviderBuilder<Builder> max() {
         return max;
      }

      @Override
      public List<Step> build() {
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing target var.");
         }
         if (min.compareTo(max) > 0) {
            throw new BenchmarkDefinitionException("min must be less than max");
         }
         return Collections.singletonList(new RandomIntStep(SessionFactory.intAccess(toVar), min.build(), max.build()));
      }
   }
}
