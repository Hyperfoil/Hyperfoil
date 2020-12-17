package io.hyperfoil.core.builders;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializablePredicate;

public interface Condition extends SerializablePredicate<Session> {
   interface Builder<B extends Builder<B>> extends BuilderBase<B> {
      Condition buildCondition();
   }

   /**
    * Selector for condition type.
    */
   class TypesBuilder<P> implements Builder<TypesBuilder<P>> {
      private final P parent;
      private IntCondition.Builder<P> intCondition;
      private StringCondition.Builder<P> stringCondition;
      private BoolCondition.Builder<P> boolCondition;
      private AllConditions.Builder<P> allConditions;

      public TypesBuilder() {
         this(null);
      }

      public TypesBuilder(P parent) {
         this.parent = parent;
      }

      private void ensureNotSet() {
         if (intCondition != null || stringCondition != null || boolCondition != null) {
            throw new BenchmarkDefinitionException("This builder can host only single condition type!");
         }
      }

      /**
       * Condition comparing integer variables.
       *
       * @return Builder
       */
      public IntCondition.Builder<P> intCondition() {
         ensureNotSet();
         return intCondition = new IntCondition.Builder<>(parent);
      }

      /**
       * Condition comparing string variables.
       *
       * @return Builder
       */
      public StringCondition.Builder<P> stringCondition() {
         ensureNotSet();
         return stringCondition = new StringCondition.Builder<>(parent);
      }

      /**
       * Condition comparing boolean variables.
       *
       * @return Builder
       */
      public BoolCondition.Builder<P> boolCondition() {
         ensureNotSet();
         return boolCondition = new BoolCondition.Builder<>(parent);
      }

      /**
       * Condition combining multiple other conditions with 'AND' logic.
       *
       * @return Builder
       */
      public AllConditions.Builder<P> allConditions() {
         ensureNotSet();
         return allConditions = new AllConditions.Builder<>(parent);
      }

      @Override
      public Condition buildCondition() {
         if (intCondition != null) {
            return intCondition.buildCondition();
         } else if (stringCondition != null) {
            return stringCondition.buildCondition();
         } else if (boolCondition != null) {
            return boolCondition.buildCondition();
         } else if (allConditions != null) {
            return allConditions.buildCondition();
         } else {
            return null;
         }
      }
   }
}
