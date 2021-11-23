package io.hyperfoil.core.steps.collections;

import io.hyperfoil.core.builders.IntCondition;
import io.hyperfoil.core.builders.IntConditionBase;
import io.hyperfoil.core.builders.IntConditionBuilder;

public class IntFilter extends IntConditionBase {
   public IntFilter(IntCondition.Predicate predicate) {
      super(predicate);
   }

   public static class Builder<P> extends IntConditionBuilder<Builder<P>, P> {
      public Builder(P parent) {
         super(parent);
      }

      public IntFilter build() {
         return new IntFilter(buildPredicate());
      }
   }
}
